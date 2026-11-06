package com.careerpilot.dto.jobmatch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchRequest {

    @NotBlank(message = "Job description is required")
    @Size(max = 6000, message = "Job description cannot exceed 6000 characters")
    @Schema(description = "Raw job description pasted by candidate (max 6000 chars)", example = "We are seeking a Backend Java Developer with 3+ years experience in Java, Spring Boot, Docker, and PostgreSQL...", maxLength = 6000)
    private String jobDescription;

    @Size(max = 255, message = "Job title cannot exceed 255 characters")
    @Schema(description = "Optional job title", example = "Senior Backend Engineer")
    private String jobTitle;

    @Size(max = 255, message = "Company name cannot exceed 255 characters")
    @Schema(description = "Optional company name", example = "Acme Corp")
    private String companyName;
}
