package com.careerpilot.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSkillUpdateRequest {

    @NotBlank(message = "Proficiency level is required")
    @Pattern(
            regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
            message = "Proficiency level must be BEGINNER, INTERMEDIATE, or ADVANCED"
    )
    private String claimedLevel;
}
