package com.careerpilot.dto.agent;

import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.SkillVerificationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifySkillsResponse {

    private String githubUsername;
    private int totalClaimedSkills;
    private int verifiedSkillsCount;
    private AgentResult<SkillVerificationResult> agentResult;
}
