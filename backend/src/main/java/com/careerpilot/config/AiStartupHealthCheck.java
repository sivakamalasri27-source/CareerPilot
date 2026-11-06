package com.careerpilot.config;

import com.careerpilot.ai.client.GeminiApiException;
import com.careerpilot.ai.client.GeminiLlmClient;
import com.careerpilot.ai.client.LlmClient;
import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Non-blocking startup health check for the configured LLM provider.
 * <p>
 * On any failure the application still starts — a WARN is logged so the operator
 * can correct the configuration without a restart loop.  The actual model name
 * that will be used is always printed, even when the check is skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStartupHealthCheck implements ApplicationRunner {

    private final LlmClient llmClient;
    private final LlmProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String provider = properties.getProvider();
        String model    = resolveEffectiveModel();

        if ("fake".equalsIgnoreCase(provider)) {
            log.info("[AI Startup] Provider=fake — live probe skipped. Model in use: {}", model);
            return;
        }

        log.info("[AI Startup] Provider={}, Model={} — running connectivity probe ...", provider, model);

        try {
            LlmRequest ping = LlmRequest.builder()
                    .prompt("Say OK")
                    .temperature(0.0)
                    .maxOutputTokens(5)
                    .build();

            LlmResponse response = llmClient.generate(ping);
            log.info("[AI Startup] LLM connectivity OK — provider={}, model={}, latency={}ms",
                    provider, model, response.getLatencyMs());

        } catch (GeminiApiException ex) {
            handleGeminiError(ex, model);
        } catch (Exception ex) {
            log.warn("[AI Startup] LLM connectivity check failed (app will still start). "
                    + "provider={}, model={}, error={}",
                    provider, model, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private void handleGeminiError(GeminiApiException ex, String model) {
        int status = ex.getStatusCode();

        if (status == 400) {
            // Bad-request usually means an unrecognised model name.
            log.warn("[AI Startup] Gemini returned HTTP 400 for model='{}'. "
                    + "This typically means the model name is invalid.",
                    model);
            if (ex.getResponseBody() != null && !ex.getResponseBody().isBlank()) {
                log.warn("[AI Startup] Gemini 400 response body: {}", ex.getResponseBody());
            }
            listAndLogModels(model);

        } else if (status == 401 || status == 403) {
            log.warn("[AI Startup] Gemini rejected the API key (HTTP {}). "
                    + "Set a valid GEMINI_API_KEY in backend/.env.",
                    status);

        } else if (status == 404) {
            log.warn("[AI Startup] Model '{}' not found (HTTP 404). "
                    + "Check LLM_MODEL in backend/.env.",
                    model);
            listAndLogModels(model);

        } else if (status == 429) {
            log.warn("[AI Startup] Gemini quota exceeded (HTTP 429). "
                    + "The key is valid but the free-tier limit was hit.");

        } else {
            log.warn("[AI Startup] Gemini connectivity probe failed with HTTP {}. "
                    + "model={}, message={}",
                    status, model, ex.getMessage());
        }

        log.warn("[AI Startup] App is starting anyway. "
                + "Fix the configuration and then verify via GET /api/v1/ai/health.");
    }

    /** Calls ListModels and logs the result — never logs the API key. */
    private void listAndLogModels(String currentModel) {
        if (llmClient instanceof GeminiLlmClient geminiClient) {
            try {
                List<String> valid = geminiClient.listAvailableModels(properties.getApiKey());
                if (valid.isEmpty()) {
                    log.warn("[AI Startup] ListModels returned no results (network error or invalid key).");
                } else {
                    log.warn("[AI Startup] Valid Gemini model IDs: {}", valid);
                    log.warn("[AI Startup] You configured model='{}'. "
                            + "Update LLM_MODEL in backend/.env to one of the listed IDs.",
                            currentModel);
                }
            } catch (Exception e) {
                log.warn("[AI Startup] Could not retrieve model list: {}", e.getMessage());
            }
        }
    }

    /** Resolves the effective model name the same way GeminiLlmClient does. */
    private String resolveEffectiveModel() {
        String m = properties.getModel();
        if (m == null || m.isBlank() || (m.startsWith("<") && m.endsWith(">"))) {
            return "gemini-2.5-flash";
        }
        if (m.startsWith("models/")) {
            return m.substring(7);
        }
        return m.trim();
    }
}
