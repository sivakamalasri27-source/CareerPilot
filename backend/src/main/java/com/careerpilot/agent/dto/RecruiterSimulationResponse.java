package com.careerpilot.agent.dto;

import com.careerpilot.agent.core.AgentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterSimulationResponse {

    private RequestType requestType;
    private AgentResult<RecruiterSimulationResult> recruiterResult;
    private Instant createdAt;
}
