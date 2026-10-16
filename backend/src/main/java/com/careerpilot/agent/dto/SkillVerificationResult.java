package com.careerpilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillVerificationResult {

    @Builder.Default
    private Double overallVerifiedPercentage = 0.0;

    @Builder.Default
    private List<SkillEvidence> skills = new ArrayList<>();

    private String summary;
}
