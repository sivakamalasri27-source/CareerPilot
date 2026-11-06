package com.careerpilot.dto.jobmatch;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMissingSkillDto {
    private String skillName;
    private String category;
}
