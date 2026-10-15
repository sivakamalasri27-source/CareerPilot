package com.careerpilot.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed audit report data")
public class AuditReportResponse {

    @Schema(description = "Audit report ID", example = "1")
    private Long id;

    @Schema(description = "Associated profile ID", example = "1")
    private Long profileId;

    @Schema(description = "Overall audit score (0-100)", example = "85")
    private Integer overallScore;

    @Schema(description = "Section scores broken down by profile section")
    private Map<String, Object> sectionScores;

    @Schema(description = "List of identified profile weaknesses with exact excerpts")
    private List<Map<String, Object>> weaknesses;

    @Schema(description = "Optional recruiter feedback from three personas")
    private Map<String, Object> recruiterFeedback;

    @Schema(description = "Request type", example = "FULL_AUDIT")
    private String requestType;

    @Schema(description = "Timestamp when report was generated")
    private Instant createdAt;
}
