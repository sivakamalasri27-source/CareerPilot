package com.careerpilot.ai.client;

import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeminiLlmClient implements LlmClient {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";
    private static final String GEMINI_LIST_MODELS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models?key={key}";

    /**
     * Ordered fallback list tried on 404 model-not-found errors.
     * The first model that succeeds is remembered for all subsequent calls.
     */
    private static final List<String> MODEL_FALLBACK_LIST = List.of(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite"
    );

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * The model name that was confirmed to work at runtime.
     * Null until a call succeeds. Volatile so all threads see the update
     * immediately without locking.
     */
    private volatile String workingModel = null;

    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int connectTimeoutMs = (properties.getConnectTimeoutSeconds() > 0 ? properties.getConnectTimeoutSeconds() : 10) * 1000;
        int readTimeoutMs = (properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 90) * 1000;
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /** Test constructor — accepts a pre-built RestClient. */
    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY / GEMINI_API_KEY is not configured for Gemini provider");
        }

        // Use the confirmed working model if we have one, else resolve from request/config.
        String preferredModel = (workingModel != null) ? workingModel : resolveModel(request.getModel());

        return callWithModelFallback(request, preferredModel, apiKey);
    }

    // -------------------------------------------------------------------------
    // Core call + fallback logic
    // -------------------------------------------------------------------------

    /**
     * Attempts the call with {@code model}. Falls back only on 404.
     * Never retries on 400/401/403/429.
     */
    private LlmResponse callWithModelFallback(LlmRequest request, String model, String apiKey) {
        try {
            LlmResponse response = callGemini(request, model, apiKey);
            rememberWorkingModel(model);
            return response;
        } catch (GeminiApiException ex) {
            if (!isFallbackCandidate(ex.getStatusCode())) {
                // 400, 401, 403, 429, 504 and other non-404 errors — propagate immediately
                throw ex;
            }
            log.warn("[AI] Model '{}' not found (HTTP 404). Trying fallback models …", model);
        }

        // Walk the fallback list on 404 only, skipping the model we just tried
        for (String fallback : MODEL_FALLBACK_LIST) {
            if (fallback.equals(model)) continue;
            try {
                LlmResponse response = callGemini(request, fallback, apiKey);
                rememberWorkingModel(fallback);
                return response;
            } catch (GeminiApiException ex) {
                if (!isFallbackCandidate(ex.getStatusCode())) {
                    throw ex;  // 400/401/403/429/504 — stop immediately
                }
                log.warn("[AI] Fallback model '{}' also returned 404. Continuing …", fallback);
            }
        }

        // All fallbacks exhausted
        throw new GeminiApiException(
                "All Gemini model fallbacks failed. Tried: " + model + " + " + MODEL_FALLBACK_LIST
                + ". Check your API key and available models.", 503);
    }

    /** Fallback only on 404 model-not-found errors. */
    private static boolean isFallbackCandidate(int status) {
        return status == 404;
    }

    /** Records the first confirmed working model and logs it (key never logged). */
    private void rememberWorkingModel(String model) {
        String cleanModel = model != null && model.startsWith("models/") ? model.substring(7) : model;
        if (!cleanModel.equals(workingModel)) {
            workingModel = cleanModel;
            log.info("[AI] Using model={}", cleanModel);
        }
    }

    // -------------------------------------------------------------------------
    // Single HTTP call — throws GeminiApiException on 4xx/timeout
    // -------------------------------------------------------------------------

    private LlmResponse callGemini(LlmRequest request, String model, String apiKey) {
        String cleanModel = model != null && model.startsWith("models/") ? model.substring(7) : model;
        Map<String, Object> requestBody = buildRequestBody(request);

        log.debug("Calling Gemini API with model: {}", cleanModel);
        long start = System.currentTimeMillis();

        String rawResponse;
        try {
            // Read response as byte[] and decode manually as UTF-8 so content type does not matter
            byte[] responseBytes = restClient.post()
                    .uri(GEMINI_API_URL, cleanModel, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);

            rawResponse = (responseBytes != null)
                    ? new String(responseBytes, StandardCharsets.UTF_8)
                    : "";
        } catch (HttpClientErrorException ex) {
            throw translateHttpException(ex, cleanModel, apiKey);
        } catch (ResourceAccessException ex) {
            if (isSocketTimeout(ex)) {
                log.error("Gemini timed out: {}", ex.getMessage());
                throw new GeminiApiException("Gemini timed out, please retry", ex, 504);
            }
            log.error("Timeout or network failure connecting to Gemini: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            if (isSocketTimeout(ex)) {
                log.error("Gemini timed out: {}", ex.getMessage());
                throw new GeminiApiException("Gemini timed out, please retry", ex, 504);
            }
            throw ex;
        }

        long latencyMs = System.currentTimeMillis() - start;
        return parseGeminiResponse(rawResponse, cleanModel, latencyMs);
    }

    private static boolean isSocketTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("read timed out") || lower.contains("connect timed out") || lower.contains("sockettimeoutexception")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Builds request body with contents/parts:
     * {"contents":[{"parts":[{"text":"..."}]}]}
     * Merges systemInstruction into prompt text to avoid unsupported field rejections.
     */
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        String prompt = request.getPrompt();
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            prompt = request.getSystemInstruction() + "\n\n" + prompt;
        }

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(textPart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(contentMap));

        if (request.getTemperature() != null) {
            requestBody.put("generationConfig", Map.of("temperature", request.getTemperature()));
        }

        return requestBody;
    }

    /** Translates an HttpClientErrorException into a GeminiApiException (never logs the key). */
    private GeminiApiException translateHttpException(HttpClientErrorException ex, String model, String apiKey) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();
        String googleError = extractGoogleErrorMessage(body, ex.getStatusText());

        log.error("[AI] Gemini API error (HTTP {}): {}", status, googleError);

        if (status == 401 || status == 403) {
            return new GeminiApiException("Gemini rejected the API key", ex, status, body);
        } else if (status == 400) {
            return new GeminiApiException(
                    "Gemini returned HTTP 400 for model '" + model + "': " + googleError,
                    ex, 400, body);
        } else if (status == 404) {
            return new GeminiApiException(
                    "Model '" + model + "' not found (HTTP 404): " + googleError,
                    ex, 404, body);
        } else if (status == 429) {
            return new GeminiApiException("Gemini API quota exceeded (HTTP 429)", ex, 429, body);
        } else {
            return new GeminiApiException(
                    "Gemini API client error (HTTP " + status + "): " + googleError, ex, status, body);
        }
    }

    private String extractGoogleErrorMessage(String responseBody, String defaultMessage) {
        if (responseBody == null || responseBody.isBlank()) {
            return defaultMessage;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode()) {
                String msg = errorNode.path("message").asText();
                if (msg != null && !msg.isBlank()) {
                    return msg;
                }
            }
        } catch (Exception ignored) {
        }
        return defaultMessage;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String resolveModel(String requestModel) {
        String m = (requestModel != null && !requestModel.isBlank()) ? requestModel : properties.getModel();
        if (m == null || m.isBlank() || (m.startsWith("<") && m.endsWith(">")) || "gemini-2.5-flash".equalsIgnoreCase(m.trim())) {
            return "gemini-flash-latest";
        }
        if (m.startsWith("models/")) {
            m = m.substring(7);
        }
        if ("gemini-2.5-flash".equalsIgnoreCase(m.trim())) {
            return "gemini-flash-latest";
        }
        return m.trim();
    }

    public List<String> listAvailableModels(String apiKey) {
        try {
            byte[] bytes = restClient.get()
                    .uri(GEMINI_LIST_MODELS_URL, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(byte[].class);

            String modelsJson = (bytes != null) ? new String(bytes, StandardCharsets.UTF_8) : "";
            JsonNode root = objectMapper.readTree(modelsJson);
            List<String> modelNames = new ArrayList<>();
            JsonNode modelsNode = root.path("models");
            if (modelsNode.isArray()) {
                for (JsonNode m : modelsNode) {
                    String name = m.path("name").asText();
                    if (!name.isBlank()) modelNames.add(name);
                }
            }
            return modelNames;
        } catch (Exception e) {
            log.warn("Could not retrieve available model list from Gemini: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private LlmResponse parseGeminiResponse(String responseJson, String model, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // Check for promptFeedback block first
            JsonNode promptFeedback = root.path("promptFeedback");
            String blockReason = promptFeedback.path("blockReason").asText(null);
            if (blockReason != null && !blockReason.isBlank()) {
                throw new IllegalStateException("Gemini generation blocked: promptFeedback.blockReason=" + blockReason);
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                String feedbackStr = !promptFeedback.isMissingNode() ? promptFeedback.toString() : "none";
                throw new IllegalStateException("Gemini returned no candidates in response (promptFeedback=" + feedbackStr + ")");
            }

            JsonNode firstCandidate = candidates.get(0);
            String finishReason = firstCandidate.path("finishReason").asText(null);

            JsonNode parts = firstCandidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("Gemini returned empty candidate content (finishReason="
                        + (finishReason != null ? finishReason : "UNKNOWN") + ")");
            }
            String text = parts.get(0).path("text").asText();
            if (text == null || text.isBlank()) {
                if (finishReason != null && !"STOP".equalsIgnoreCase(finishReason)) {
                    throw new IllegalStateException("Gemini generation stopped unexpectedly with finishReason=" + finishReason);
                }
            }

            JsonNode usage = root.path("usageMetadata");
            int promptTokens = usage.path("promptTokenCount").asInt(0);
            int completionTokens = usage.path("candidatesTokenCount").asInt(0);
            int totalTokens = usage.path("totalTokenCount").asInt(promptTokens + completionTokens);

            return LlmResponse.builder()
                    .content(text != null ? text : "")
                    .modelName(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .latencyMs(latencyMs)
                    .build();
        } catch (IllegalStateException e) {
            log.error("Gemini response validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseJson, e);
            throw new IllegalStateException("Failed to parse Gemini response JSON", e);
        }
    }
}
