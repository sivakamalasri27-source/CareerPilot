package com.careerpilot.dto.suggestion;

import com.careerpilot.entity.SuggestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionResponse {

    private Long id;
    private Long profileId;
    private String agentName;
    private String targetSection;
    private Long targetId;
    private String originalText;
    private String suggestedText;
    private List<String> suggestedBullets;
    private String rationale;
    private SuggestionStatus status;
    private Instant createdAt;
    private Instant decidedAt;
}
