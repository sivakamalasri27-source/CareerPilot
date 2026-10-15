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
@Schema(description = "Request to trigger recruiter panel simulation")
public class RecruiterSimulationRequest {

    @Schema(description = "Optional target role override. If omitted, uses profile's target role.", example = "Frontend Engineer")
    private String targetRole;
}
