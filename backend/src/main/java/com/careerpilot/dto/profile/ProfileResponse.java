package com.careerpilot.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private Long id;
    private Long userId;
    private String headline;
    private String about;
    private String githubUsername;
    private String targetRole;
    private String targetIndustry;
    private String phone;
    private String location;
    private String linkedinUrl;
    private String portfolioUrl;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
