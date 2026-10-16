package com.careerpilot.dto.resume;

import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadResponse {

    private Long id;
    private String fileName;
    private Long fileSizeBytes;
    private UploadStatus status;
    private int extractedTextLength;
    private int createdSuggestionsCount;

    @Builder.Default
    private List<SuggestionResponse> suggestions = new ArrayList<>();

    private Instant createdAt;
}
