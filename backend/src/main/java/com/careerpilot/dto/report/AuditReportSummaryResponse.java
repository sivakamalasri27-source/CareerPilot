package com.careerpilot.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary audit report for paginated lists")
public class AuditReportSummaryResponse {

    @Schema(description = "Audit report ID", example = "1")
    private Long id;

    @Schema(description = "Overall audit score (0-100)", example = "85")
    private Integer overallScore;

    @Schema(description = "Request type", example = "FULL_AUDIT")
    private String requestType;

    @Schema(description = "Timestamp when report was generated")
    private Instant createdAt;
}
