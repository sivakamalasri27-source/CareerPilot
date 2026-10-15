package com.careerpilot.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to trigger a full profile audit")
public class AuditRequest {

    @Schema(description = "Optional target role override. If omitted, uses profile's target role.", example = "Java Backend Developer")
    private String targetRole;
}
