package com.careerpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    private String prompt;
    private String systemInstruction;
    private String model;

    @Builder.Default
    private Double temperature = 0.2;

    @Builder.Default
    private Integer maxOutputTokens = 2048;
}
