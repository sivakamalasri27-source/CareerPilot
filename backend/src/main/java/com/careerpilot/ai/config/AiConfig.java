package com.careerpilot.ai.config;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.ai.client.GeminiLlmClient;
import com.careerpilot.ai.client.LlmClient;
import com.careerpilot.config.DotenvEnvironmentPostProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
    public LlmClient geminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        validateGeminiConfiguration(properties);
        return new GeminiLlmClient(properties, objectMapper);
    }

    public static void validateGeminiConfiguration(LlmProperties properties) {
        String apiKey = properties != null ? properties.getApiKey() : null;

        File dotenvFile = DotenvEnvironmentPostProcessor.findDotenvFile();
        String checkedPath = (dotenvFile != null) ? dotenvFile.getAbsolutePath() : "backend/.env";
        boolean fileFound = dotenvFile != null && dotenvFile.exists() && dotenvFile.isFile();

        if (apiKey == null || apiKey.trim().isBlank()) {
            throw new IllegalStateException(String.format(
                    "GEMINI_API_KEY / LLM_API_KEY is not configured or blank for provider 'gemini'. " +
                    "Checked path: %s (file found: %s). Please set GEMINI_API_KEY in backend/.env or your environment variables.",
                    checkedPath, fileFound ? "yes" : "no"));
        }

        String trimmed = apiKey.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("your_gemini_api_key_here")
                || lower.equals("your_api_key_here")
                || lower.contains("placeholder")
                || (lower.startsWith("<") && lower.endsWith(">"))
                || lower.equals("mock-key")
                || lower.equals("changeme")
                || lower.equals("dummy")) {
            throw new IllegalStateException(String.format(
                    "GEMINI_API_KEY / LLM_API_KEY is configured with placeholder value: '%s'. " +
                    "Checked path: %s (file found: %s). Please configure a valid Gemini API key from Google AI Studio.",
                    trimmed, checkedPath, fileFound ? "yes" : "no"));
        }

        if (!trimmed.startsWith("AIza")) {
            log.warn("Configured Gemini API key does not start with expected 'AIza' prefix. " +
                     "Key may be invalid or malformed.");
        }

        if (properties.getModel() == null || properties.getModel().isBlank()
                || (properties.getModel().startsWith("<") && properties.getModel().endsWith(">"))
                || "gemini-2.5-flash".equalsIgnoreCase(properties.getModel().trim())) {
            log.warn("Configured LLM model '{}' is empty, placeholder, or gemini-2.5-flash. Resetting to 'gemini-flash-latest'.", properties.getModel());
            properties.setModel("gemini-flash-latest");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "fake")
    public FakeLlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }

    @Bean
    public Cache<String, Object> aiResultCache(LlmProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getCache().getTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(properties.getCache().getMaxSize())
                .recordStats()
                .build();
    }
}
