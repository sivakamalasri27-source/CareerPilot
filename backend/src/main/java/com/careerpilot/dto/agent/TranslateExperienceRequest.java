package com.careerpilot.dto.agent;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslateExperienceRequest {

    private Long projectId;

    private String title;

    private String description;

    private String technologies;

    private List<@Size(max = 300, message = "Each bullet must not exceed 300 characters") String> bullets;

    private String role;

    private String targetRole;
}
