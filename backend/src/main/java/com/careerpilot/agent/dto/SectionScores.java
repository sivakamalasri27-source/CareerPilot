package com.careerpilot.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionScores {

    @NotNull(message = "Headline score is required")
    @Min(value = 0, message = "Headline score must be at least 0")
    @Max(value = 100, message = "Headline score cannot exceed 100")
    private Integer headline;

    @NotNull(message = "About score is required")
    @Min(value = 0, message = "About score must be at least 0")
    @Max(value = 100, message = "About score cannot exceed 100")
    private Integer about;

    @NotNull(message = "Skills score is required")
    @Min(value = 0, message = "Skills score must be at least 0")
    @Max(value = 100, message = "Skills score cannot exceed 100")
    private Integer skills;

    @NotNull(message = "Projects score is required")
    @Min(value = 0, message = "Projects score must be at least 0")
    @Max(value = 100, message = "Projects score cannot exceed 100")
    private Integer projects;

    @NotNull(message = "Education score is required")
    @Min(value = 0, message = "Education score must be at least 0")
    @Max(value = 100, message = "Education score cannot exceed 100")
    private Integer education;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("headline", headline);
        map.put("about", about);
        map.put("skills", skills);
        map.put("projects", projects);
        map.put("education", education);
        return map;
    }
}
