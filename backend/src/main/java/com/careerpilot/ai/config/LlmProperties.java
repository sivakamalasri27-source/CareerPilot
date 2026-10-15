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
    private String model = "gemini-1.5-flash";
    private int timeoutSeconds = 15;
    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();

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
