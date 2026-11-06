package com.careerpilot.dto.jobmatch;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchComputationResult {
    private String jobTitle;
    private String companyName;
    private String jobDescription;
    private String extractedExperience;
    private Double matchPercentage;
    @Builder.Default
    private List<JobMatchedSkillDto> matchedSkills = new ArrayList<>();
    @Builder.Default
    private List<JobMissingSkillDto> missingSkills = new ArrayList<>();
}
