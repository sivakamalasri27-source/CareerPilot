package com.careerpilot.agent.dto;

import jakarta.validation.Valid;
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
public class RecruiterSimulationResult {

    @NotNull(message = "Personas list is required")
    @Valid
    @Builder.Default
    private List<PersonaFeedback> personas = new ArrayList<>();

    @NotNull(message = "Consensus decision is required")
    private RecruiterDecision consensusDecision;

    private String summary;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> personaMaps = new ArrayList<>();
        if (personas != null) {
            for (PersonaFeedback pf : personas) {
                personaMaps.add(pf.toMap());
            }
        }
        map.put("personas", personaMaps);
        map.put("consensusDecision", consensusDecision != null ? consensusDecision.name() : null);
        map.put("summary", summary);
        return map;
    }
}
