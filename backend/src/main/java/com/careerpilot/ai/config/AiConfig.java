package com.careerpilot.ai.config;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.ai.client.GeminiLlmClient;
import com.careerpilot.ai.client.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
    public LlmClient geminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        return new GeminiLlmClient(properties, objectMapper);
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
