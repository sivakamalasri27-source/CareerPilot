package com.careerpilot.dto.jobmatch;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResponse {
    private Long id;
    private String jobTitle;
    private String companyName;
    private String extractedExperience;
    private Double matchPercentage;
    @Builder.Default
    private List<JobMatchedSkillDto> matchedSkills = new ArrayList<>();
    @Builder.Default
    private List<JobMissingSkillDto> missingSkills = new ArrayList<>();
    private String fitSummary;
    @Builder.Default
    private List<String> profileTweaks = new ArrayList<>();
    @Builder.Default
    private List<String> interviewTopics = new ArrayList<>();
    private Instant createdAt;
}
