package com.careerpilot.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class ProfileAuditResult {

    @NotNull(message = "Overall score is required")
    @Min(value = 0, message = "Overall score must be at least 0")
    @Max(value = 100, message = "Overall score cannot exceed 100")
    private Integer overallScore;

    @NotNull(message = "Section scores are required")
    @Valid
    private SectionScores sectionScores;

    @NotNull(message = "Weaknesses list is required")
    @Valid
    @Builder.Default
    private List<ProfileWeakness> weaknesses = new ArrayList<>();

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Builder.Default
    private List<String> keyRecommendations = new ArrayList<>();
}
