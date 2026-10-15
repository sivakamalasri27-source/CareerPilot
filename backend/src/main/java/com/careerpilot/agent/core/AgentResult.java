package com.careerpilot.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult<O> {

    private AgentStatus status;
    private O data;
    private String errorMessage;

    public static <O> AgentResult<O> success(O data) {
        return AgentResult.<O>builder()
                .status(AgentStatus.SUCCESS)
                .data(data)
                .build();
    }

    public static <O> AgentResult<O> fallback(O fallbackData, String reason) {
        return AgentResult.<O>builder()
                .status(AgentStatus.FALLBACK)
                .data(fallbackData)
                .errorMessage(reason)
                .build();
    }

    public static <O> AgentResult<O> failure(String errorMessage) {
        return AgentResult.<O>builder()
                .status(AgentStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    public boolean isSuccessful() {
        return status == AgentStatus.SUCCESS;
    }
}
