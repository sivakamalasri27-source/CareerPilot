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
public class SkillEvidence {

    private String skillName;
    private String claimedLevel;
    private SkillVerificationStatus status;

    @Builder.Default
    private List<RepoEvidence> evidence = new ArrayList<>();

    private String explanation;
}
