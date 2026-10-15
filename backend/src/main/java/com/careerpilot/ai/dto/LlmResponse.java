package com.careerpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {
    private String content;
    private String modelName;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
}
