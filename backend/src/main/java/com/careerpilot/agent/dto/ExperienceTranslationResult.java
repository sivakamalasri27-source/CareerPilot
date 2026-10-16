package com.careerpilot.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceTranslationResult {

    @Builder.Default
    @Size(max = 8, message = "Maximum of 8 bullets allowed")
    private List<@Size(max = 300, message = "Each bullet must not exceed 300 characters") String> bullets = new ArrayList<>();

    @Builder.Default
    private List<String> clarifyingQuestions = new ArrayList<>();

    private String rationale;
}
