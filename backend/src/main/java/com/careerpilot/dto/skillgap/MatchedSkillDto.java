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
public class MatchedSkillDto {
    private String skillName;
    private String claimedLevel;
    private String verificationStatus;
    private Integer weight;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("skillName", skillName);
        map.put("claimedLevel", claimedLevel);
        map.put("verificationStatus", verificationStatus);
        map.put("weight", weight);
        return map;
    }
}
