package com.careerpilot.dto.profile;

import com.careerpilot.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSkillResponse {

    private Long id;
    private Long skillId;
    private String name;
    private String category;
    private String claimedLevel;
    private VerificationStatus verificationStatus;
    private String evidenceSummary;
    private String evidenceUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
