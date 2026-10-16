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
public class RoadmapItemDto {
    private String skillName;
    private String priority;
    private String whyItMatters;
    private String whatToLearn;
    private String smallProjectIdea;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("skillName", skillName);
        map.put("priority", priority);
        map.put("whyItMatters", whyItMatters);
        map.put("whatToLearn", whatToLearn);
        map.put("smallProjectIdea", smallProjectIdea);
        return map;
    }
}
