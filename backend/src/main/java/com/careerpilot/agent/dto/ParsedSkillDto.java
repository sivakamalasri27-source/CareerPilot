package com.careerpilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedSkillDto {

    private String name;
    private String proficiency; // BEGINNER, INTERMEDIATE, ADVANCED
}
