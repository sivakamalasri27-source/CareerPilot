package com.careerpilot.ai.service;

import com.careerpilot.ai.client.LlmClient;
import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import com.careerpilot.ai.prompt.PromptLoader;
import com.careerpilot.ai.prompt.PromptSanitizer;
import com.careerpilot.ai.ratelimit.RateLimiterService;
import com.careerpilot.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiExecutionService {

    public static final int MAX_REPAIR_ATTEMPTS = 2;

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final PromptLoader promptLoader;
    private final RateLimiterService rateLimiterService;
    private final TokenUsageTracker tokenUsageTracker;
    private final Cache<String, Object> aiResultCache;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Executes an AI agent task with rate limiting, caching, circuit breaker,
     * injection defense, typed validation, and an automatic repair loop.
     */
    public <T> T execute(
            User user,
            String agentName,
            String promptFileName,
            Map<String, String> promptVariables,
            String rawUserData,
            Class<T> responseType,
            Supplier<T> fallbackSupplier
    ) {
        // 1. Rate limiting per user
        if (user != null && user.getId() != null) {
            rateLimiterService.consumeOrThrow(user.getId());
        }

        // 2. Prepare variables and sanitize user data
        Map<String, String> variables = new HashMap<>();
        if (promptVariables != null) {
            variables.putAll(promptVariables);
        }
        if (rawUserData != null && !rawUserData.isBlank()) {
            String wrappedData = PromptSanitizer.wrapUserData(rawUserData);
            variables.put("candidateData", wrappedData);
        }

        // 3. Render prompt (fails fast on missing placeholders)
        String renderedPrompt = promptLoader.render(promptFileName, variables);
        String promptVersion = promptLoader.extractVersion(promptFileName);
        String model = properties.getModel();

        // 4. Compute cache key and check Caffeine cache
        String cacheKey = computeCacheKey(promptVersion, model, renderedPrompt, rawUserData);
        Object cached = aiResultCache.getIfPresent(cacheKey);
        if (cached != null && responseType.isInstance(cached)) {
            log.info("AI Cache HIT for key={}, agent={}", cacheKey, agentName);
            return responseType.cast(cached);
        }
        log.debug("AI Cache MISS for key={}, agent={}", cacheKey, agentName);

        // 5. Build LLM request
        LlmRequest initialRequest = LlmRequest.builder()
                .prompt(renderedPrompt)
                .systemInstruction(PromptSanitizer.SYSTEM_DATA_BOUNDARY_INSTRUCTION)
                .model(model)
                .temperature(0.2)
                .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmService");

        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        long totalDurationMs = 0;

        // 6. Execute initial call through CircuitBreaker with transient retry
        LlmResponse initialResponse;
        try {
            initialResponse = circuitBreaker.executeSupplier(() -> executeWithTransientRetry(initialRequest));
        } catch (CallNotPermittedException e) {
            log.error("Circuit breaker is OPEN for llmService: {}", e.getMessage());
            throw e;
        }

        totalPromptTokens += initialResponse.getPromptTokens();
        totalCompletionTokens += initialResponse.getCompletionTokens();
        totalDurationMs += initialResponse.getLatencyMs();

        // 7. Validate JSON output
        ValidationResult<T> vr = parseAndValidate(initialResponse.getContent(), responseType);
        if (vr.isValid()) {
            aiResultCache.put(cacheKey, vr.result());
            tokenUsageTracker.recordUsage(user, agentName, model, totalPromptTokens, totalCompletionTokens, totalDurationMs, true);
            return vr.result();
        }

        // 8. Repair loop (up to MAX_REPAIR_ATTEMPTS retries)
        String lastMalformedContent = initialResponse.getContent();
        for (int attempt = 1; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            log.warn("LLM output validation failed for {}. Repair attempt {}/{}. Reason: {}",
                    agentName, attempt, MAX_REPAIR_ATTEMPTS, vr.errorMessage());

            Map<String, String> repairVars = Map.of(
                    "jsonSchema", responseType.getSimpleName(),
                    "validationErrors", vr.errorMessage(),
                    "malformedJson", lastMalformedContent != null ? lastMalformedContent : ""
            );
            String repairPrompt = promptLoader.render("repair-json.v1.txt", repairVars);

            LlmRequest repairRequest = LlmRequest.builder()
                    .prompt(repairPrompt)
                    .systemInstruction(PromptSanitizer.SYSTEM_DATA_BOUNDARY_INSTRUCTION)
                    .model(model)
                    .temperature(0.1)
                    .build();

            try {
                LlmResponse repairResponse = circuitBreaker.executeSupplier(() -> executeWithTransientRetry(repairRequest));
                totalPromptTokens += repairResponse.getPromptTokens();
                totalCompletionTokens += repairResponse.getCompletionTokens();
                totalDurationMs += repairResponse.getLatencyMs();
                lastMalformedContent = repairResponse.getContent();

                vr = parseAndValidate(repairResponse.getContent(), responseType);
                if (vr.isValid()) {
                    log.info("Repair succeeded for {} on attempt {}", agentName, attempt);
                    aiResultCache.put(cacheKey, vr.result());
                    tokenUsageTracker.recordUsage(user, agentName, model, totalPromptTokens, totalCompletionTokens, totalDurationMs, true);
                    return vr.result();
                }
            } catch (CallNotPermittedException e) {
                throw e;
            } catch (Exception ex) {
                log.warn("Repair attempt {} failed with error: {}", attempt, ex.getMessage());
            }
        }

        // 9. All repair attempts exhausted -> Return graceful fallback
        log.error("Exhausted all {} repair attempts for agent {}. Returning graceful fallback.", MAX_REPAIR_ATTEMPTS, agentName);
        tokenUsageTracker.recordUsage(user, agentName, model, totalPromptTokens, totalCompletionTokens, totalDurationMs, false);

        if (fallbackSupplier != null) {
            return fallbackSupplier.get();
        }
        throw new IllegalStateException("Failed to produce valid JSON from LLM and no fallback was provided");
    }

    /**
     * Executes LLM call with retry for transient errors (timeouts, 5xx), but fails fast on 4xx.
     */
    public LlmResponse executeWithTransientRetry(LlmRequest request) {
        int maxAttempts = 3;
        long backoffMs = 200L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return llmClient.generate(request);
            } catch (HttpClientErrorException ex) {
                // 4xx client errors are permanent configuration/request issues; NEVER retry!
                log.error("LLM client error (4xx): status={}, message={}", ex.getStatusCode(), ex.getMessage());
                throw ex;
            } catch (ResourceAccessException | HttpServerErrorException ex) {
                // Transient network / timeout / 5xx error
                log.warn("Transient error calling LLM on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
                if (attempt == maxAttempts) {
                    throw ex;
                }
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            } catch (RuntimeException ex) {
                log.error("Non-transient error calling LLM: {}", ex.getMessage());
                throw ex;
            }
        }
        throw new IllegalStateException("Unexpected completion of retry loop without response or exception");
    }

    /**
     * Parses JSON string and validates with Jakarta Validator.
     */
    public <T> ValidationResult<T> parseAndValidate(String rawJson, Class<T> responseType) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ValidationResult<>(false, null, "Raw LLM output is empty or null");
        }

        String cleanedJson = stripMarkdownFences(rawJson);
        T parsed;
        try {
            parsed = objectMapper.readValue(cleanedJson, responseType);
        } catch (Exception e) {
            return new ValidationResult<>(false, null, "Malformed JSON syntax: " + e.getMessage());
        }

        Set<ConstraintViolation<T>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Field constraint violations: ");
            for (ConstraintViolation<T> v : violations) {
                sb.append(v.getPropertyPath()).append(" (").append(v.getMessage()).append("); ");
            }
            return new ValidationResult<>(false, parsed, sb.toString());
        }

        return new ValidationResult<>(true, parsed, null);
    }

    public static String stripMarkdownFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();

        // Extract content between ```json ... ``` or ``` ... ``` if embedded in prose
        int startFence = trimmed.indexOf("```json");
        int fenceLen = 7;
        if (startFence == -1) {
            startFence = trimmed.indexOf("```");
            fenceLen = 3;
        }

        if (startFence != -1) {
            int contentStart = trimmed.indexOf('\n', startFence + fenceLen);
            if (contentStart != -1) {
                int endFence = trimmed.indexOf("```", contentStart);
                if (endFence != -1) {
                    return trimmed.substring(contentStart + 1, endFence).trim();
                }
            }
        }

        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public static String computeCacheKey(String promptVersion, String model, String prompt, String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (promptVersion != null ? promptVersion : "") + "|"
                    + (model != null ? model : "") + "|"
                    + (prompt != null ? prompt : "") + "|"
                    + (input != null ? input : "");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ValidationResult<T>(boolean isValid, T result, String errorMessage) {}
}
