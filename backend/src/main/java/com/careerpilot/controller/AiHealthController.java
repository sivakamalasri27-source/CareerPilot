package com.careerpilot.controller;

import com.careerpilot.ai.client.LlmClient;
import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Health", description = "Endpoints for diagnosing AI and LLM service availability")
@SecurityRequirement(name = "bearerAuth")
public class AiHealthController {

    private final LlmClient llmClient;
    private final LlmProperties properties;

    @GetMapping("/health")
    @Operation(summary = "Check AI service connectivity",
            description = "Performs a lightweight probe call against the configured LLM provider and returns status.")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", llmClient.getProviderName());
        body.put("model", properties.getModel());

        try {
            LlmRequest ping = LlmRequest.builder()
                    .prompt("Say OK")
                    .temperature(0.0)
                    .maxOutputTokens(5)
                    .build();

            LlmResponse response = llmClient.generate(ping);
            body.put("status", "UP");
            body.put("message", "OK");
            body.put("latencyMs", response.getLatencyMs());
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            log.warn("AI health check probe failed: {}", ex.getMessage());
            body.put("status", "DOWN");
            body.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
