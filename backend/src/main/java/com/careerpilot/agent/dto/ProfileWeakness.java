package com.careerpilot.agent.dto;

import jakarta.validation.constraints.NotBlank;
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
public class ProfileWeakness {

    @NotBlank(message = "Section identifier is required")
    private String section;

    @NotBlank(message = "Weak text excerpt is required")
    private String weakText;

    @NotBlank(message = "Reason for weakness is required")
    private String reason;

    private String improvementSuggestion;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("section", section);
        map.put("weakText", weakText);
        map.put("reason", reason);
        if (improvementSuggestion != null) {
            map.put("improvementSuggestion", improvementSuggestion);
        }
        return map;
    }
}
