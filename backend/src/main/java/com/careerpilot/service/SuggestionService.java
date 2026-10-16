package com.careerpilot.service;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.AgentSuggestion;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.SuggestionStatus;
import com.careerpilot.entity.User;
import org.springframework.data.domain.Pageable;

public interface SuggestionService {

    PageResponse<SuggestionResponse> getSuggestions(User user, SuggestionStatus status, Pageable pageable);

    SuggestionResponse getSuggestionById(User user, Long id);

    SuggestionResponse approveSuggestion(User user, Long id, ApproveSuggestionRequest request);

    SuggestionResponse rejectSuggestion(User user, Long id);

    AgentSuggestion createSuggestion(
            Profile profile,
            String agentName,
            String targetSection,
            Long targetId,
            String originalText,
            String suggestedText,
            String rationale
    );

    SuggestionResponse mapToResponse(AgentSuggestion suggestion);

    String serializeBullets(java.util.List<String> bullets);

    java.util.List<String> parseBullets(String input);
}
