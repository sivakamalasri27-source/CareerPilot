package com.careerpilot.dto.skillgap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapResponse {
    private Long id;
    private Long roleId;
    private String roleName;
    private Double matchPercentage;
    private List<MatchedSkillDto> matchedSkills;
    private List<MissingSkillDto> missingSkills;
    private List<WeakSkillDto> weakSkills;
    private List<RoadmapItemDto> learningRoadmap;
    private String summary;
    private Instant createdAt;
}
