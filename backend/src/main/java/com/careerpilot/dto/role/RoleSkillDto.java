package com.careerpilot.dto.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSkillDto {
    private String skillName;
    private String category;
    private Integer weight;
    private String priority;
}
