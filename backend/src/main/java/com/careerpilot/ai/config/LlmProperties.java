package com.careerpilot.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String provider = "gemini";
    private String apiKey = "";
    private String model = "gemini-flash-latest";
    private int timeoutSeconds = 90;
    private int connectTimeoutSeconds = 10;
    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();

    private boolean apiKeyExplicitlySet = false;

    public void setApiKey(String apiKey) {
        this.apiKey = sanitizeKey(apiKey);
        this.apiKeyExplicitlySet = true;
    }

    public String getApiKey() {
        if (apiKeyExplicitlySet) {
            return apiKey;
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return sanitizeKey(apiKey);
        }
        // Fallback to environment variables: GEMINI_API_KEY first, then LLM_API_KEY
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank()) {
            return sanitizeKey(geminiKey);
        }
        String llmKey = System.getenv("LLM_API_KEY");
        if (llmKey != null && !llmKey.isBlank()) {
            return sanitizeKey(llmKey);
        }
        return "";
    }

    private static String sanitizeKey(String key) {
        if (key == null) return "";
        String trimmed = key.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    @Data
    public static class RateLimit {
        private int capacity = 20;
        private int refillTokens = 20;
        private int refillDurationSeconds = 60;
    }

    @Data
    public static class Cache {
        private int ttlMinutes = 60;
        private int maxSize = 1000;
    }
}
