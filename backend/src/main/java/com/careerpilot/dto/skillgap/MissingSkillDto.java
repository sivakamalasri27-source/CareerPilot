package com.careerpilot.dto.skillgap;

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
public class MissingSkillDto {
    private String skillName;
    private String category;
    private String priority;
    private Integer weight;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("skillName", skillName);
        map.put("category", category);
        map.put("priority", priority);
        map.put("weight", weight);
        return map;
    }
}
