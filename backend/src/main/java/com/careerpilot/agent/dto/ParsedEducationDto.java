package com.careerpilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEducationDto {

    private String institution;
    private String degree;
    private String fieldOfStudy;
    private String gradeOrGpa;
    private String startDate;
    private String endDate;
}
