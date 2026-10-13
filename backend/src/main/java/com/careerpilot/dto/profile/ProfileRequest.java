package com.careerpilot.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {

    @NotBlank(message = "Headline is required and cannot be blank")
    @Size(max = 120, message = "Headline cannot exceed 120 characters")
    private String headline;

    @Size(max = 2000, message = "About section cannot exceed 2000 characters")
    private String about;

    @Size(max = 100, message = "GitHub username cannot exceed 100 characters")
    private String githubUsername;

    @Size(max = 100, message = "Target role cannot exceed 100 characters")
    private String targetRole;

    @Size(max = 100, message = "Target industry cannot exceed 100 characters")
    private String targetIndustry;

    @Size(max = 50, message = "Phone number cannot exceed 50 characters")
    private String phone;

    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location;

    @Size(max = 255, message = "LinkedIn URL cannot exceed 255 characters")
    private String linkedinUrl;

    @Size(max = 255, message = "Portfolio URL cannot exceed 255 characters")
    private String portfolioUrl;

    private Long version;
}
