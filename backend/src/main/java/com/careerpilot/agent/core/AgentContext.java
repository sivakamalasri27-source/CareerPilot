package com.careerpilot.agent.core;

import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
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
public class AgentContext<I> {

    private User user;
    private Profile profile;
    private I input;
    private String targetRole;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
