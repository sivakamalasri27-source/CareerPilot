package com.careerpilot.dto.skillgap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapComputationResult {
    private Long roleId;
    private String roleName;
    private String roleDescription;
    private Double matchPercentage;
    private List<MatchedSkillDto> matchedSkills;
    private List<MissingSkillDto> missingSkills;
    private List<WeakSkillDto> weakSkills;
}
