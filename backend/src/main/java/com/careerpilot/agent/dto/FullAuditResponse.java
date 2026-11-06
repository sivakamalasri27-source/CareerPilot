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
public class FullAuditResponse {

    private Long reportId;
    private RequestType requestType;
    private AgentResult<ProfileAuditResult> auditorResult;
    private AgentResult<RecruiterSimulationResult> recruiterResult;
    private Instant createdAt;
    private boolean unchanged;

    public Integer getOverallScore() {
        if (auditorResult != null && auditorResult.getData() != null) {
            return auditorResult.getData().getOverallScore();
        }
        return null;
    }

    public Long getAuditId() {
        return reportId;
    }
}
