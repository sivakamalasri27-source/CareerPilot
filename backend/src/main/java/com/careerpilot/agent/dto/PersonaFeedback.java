package com.careerpilot.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaFeedback {

    @NotNull(message = "Persona type is required")
    private PersonaType persona;

    @NotBlank(message = "Persona title is required")
    private String personaTitle;

    @NotBlank(message = "10-second first impression is required")
    private String firstImpression10Sec;

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    @NotNull(message = "Hiring decision is required")
    private RecruiterDecision decision;

    @NotBlank(message = "Decision reasoning is required")
    private String reasoning;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("persona", persona != null ? persona.name() : null);
        map.put("personaTitle", personaTitle);
        map.put("firstImpression10Sec", firstImpression10Sec);
        map.put("strengths", strengths);
        map.put("redFlags", redFlags);
        map.put("decision", decision != null ? decision.name() : null);
        map.put("reasoning", reasoning);
        return map;
    }
}
