package com.careerpilot.ai.client;

import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeminiLlmClient implements LlmClient {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = properties.getTimeoutSeconds() * 1000;
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String model = request.getModel() != null ? request.getModel() : properties.getModel();
        String apiKey = properties.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY / GEMINI_API_KEY is not configured for Gemini provider");
        }

        Map<String, Object> requestBody = new HashMap<>();

        // 1. User contents
        Map<String, Object> textPart = Map.of("text", request.getPrompt());
        Map<String, Object> contentMap = Map.of(
                "role", "user",
                "parts", List.of(textPart)
        );
        requestBody.put("contents", List.of(contentMap));

        // 2. System instruction (if present)
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            requestBody.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemInstruction()))
            ));
        }

        // 3. Generation config (JSON mode)
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", request.getTemperature());
        generationConfig.put("responseMimeType", "application/json");
        if (request.getMaxOutputTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxOutputTokens());
        }
        requestBody.put("generationConfig", generationConfig);

        log.debug("Calling Gemini API with model: {}", model);
        long start = System.currentTimeMillis();

        String rawResponse = restClient.post()
                .uri(GEMINI_API_URL, model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        long latencyMs = System.currentTimeMillis() - start;

        return parseGeminiResponse(rawResponse, model, latencyMs);
    }

    private LlmResponse parseGeminiResponse(String responseJson, String model, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty()) {
                throw new IllegalStateException("Gemini returned no candidates in response");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode content = firstCandidate.path("content");
            JsonNode parts = content.path("parts");
            String text = parts.isEmpty() ? "" : parts.get(0).path("text").asText();

            JsonNode usage = root.path("usageMetadata");
            int promptTokens = usage.path("promptTokenCount").asInt(0);
            int completionTokens = usage.path("candidatesTokenCount").asInt(0);
            int totalTokens = usage.path("totalTokenCount").asInt(promptTokens + completionTokens);

            return LlmResponse.builder()
                    .content(text)
                    .modelName(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseJson, e);
            throw new IllegalStateException("Failed to parse Gemini response JSON", e);
        }
    }
}
