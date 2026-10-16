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
public class ParsedResumeResult {

    private String headline;
    private String about;

    @Builder.Default
    private List<ParsedSkillDto> skills = new ArrayList<>();

    @Builder.Default
    private List<ParsedProjectDto> projects = new ArrayList<>();

    @Builder.Default
    private List<ParsedEducationDto> education = new ArrayList<>();
}
