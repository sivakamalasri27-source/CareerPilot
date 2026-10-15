package com.careerpilot.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestAiAuditRequest {

    @NotBlank(message = "Candidate data cannot be blank")
    private String candidateData;

    @NotBlank(message = "Target role cannot be blank")
    private String targetRole;
}
