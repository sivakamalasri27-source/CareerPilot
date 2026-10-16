package com.careerpilot.agent.dto;

import com.careerpilot.dto.skillgap.MatchedSkillDto;
import com.careerpilot.dto.skillgap.MissingSkillDto;
import com.careerpilot.dto.skillgap.RoadmapItemDto;
import com.careerpilot.dto.skillgap.WeakSkillDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapResult {
    private Long roleId;
    private String roleName;
    private Double matchPercentage;
    private List<MatchedSkillDto> matchedSkills;
    private List<MissingSkillDto> missingSkills;
    private List<WeakSkillDto> weakSkills;
    private List<RoadmapItemDto> learningRoadmap;
    private String summary;
}
