package com.careerpilot.dto.agent;

import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.ExperienceTranslationResult;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslateExperienceResponse {

    private AgentResult<ExperienceTranslationResult> agentResult;
    private SuggestionResponse suggestion;
}
