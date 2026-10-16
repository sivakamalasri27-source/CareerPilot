package com.careerpilot.dto.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetRoleResponse {
    private Long id;
    private String name;
    private String description;
    private int requiredSkillsCount;
    private List<RoleSkillDto> requiredSkills;
}
