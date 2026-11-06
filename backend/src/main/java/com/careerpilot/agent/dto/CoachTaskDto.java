package com.careerpilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachTaskDto {
    private String title;
    private String description;
    private String category;
    private String estimatedTime;
    private String whyThisMatters;
    private String verificationType;
    private String verificationHint;
}
