package com.careerpilot.dto.jobmatch;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchedSkillDto {
    private String skillName;
    private String claimedLevel;
    private String verificationStatus; // VERIFIED, PARTIAL, UNVERIFIED
}
