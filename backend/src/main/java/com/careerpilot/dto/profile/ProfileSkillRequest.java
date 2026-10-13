package com.careerpilot.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSkillRequest {

    @NotBlank(message = "Skill name is required and cannot be blank")
    @Size(max = 100, message = "Skill name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Proficiency level is required")
    @Pattern(
            regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
            message = "Proficiency level must be BEGINNER, INTERMEDIATE, or ADVANCED"
    )
    private String claimedLevel;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
}
