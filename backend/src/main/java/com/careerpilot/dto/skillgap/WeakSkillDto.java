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
public class WeakSkillDto {
    private String skillName;
    private String claimedLevel;
    private String reason;
    private Integer weight;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("skillName", skillName);
        map.put("claimedLevel", claimedLevel);
        map.put("reason", reason);
        map.put("weight", weight);
        return map;
    }
}
