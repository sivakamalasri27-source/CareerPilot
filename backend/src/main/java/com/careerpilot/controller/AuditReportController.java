package com.careerpilot.controller;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.report.AuditReportResponse;
import com.careerpilot.dto.report.AuditReportSummaryResponse;
import com.careerpilot.entity.AuditReport;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.AuditReportRepository;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Audit Reports", description = "Endpoints for retrieving candidate profile audit history")
@SecurityRequirement(name = "bearerAuth")
public class AuditReportController {

    private final AuditReportRepository auditReportRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get paginated audit reports",
            description = "Retrieves paginated audit report summaries for the authenticated user's profile.")
    public ResponseEntity<PageResponse<AuditReportSummaryResponse>> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        Profile profile = resolveUserProfile(user.getId());

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditReport> reportPage = auditReportRepository.findAllByProfileIdOrderByCreatedAtDesc(profile.getId(), pageable);

        Page<AuditReportSummaryResponse> dtoPage = reportPage.map(this::mapToSummaryResponse);
        return ResponseEntity.ok(PageResponse.from(dtoPage));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed audit report by ID",
            description = "Retrieves full report details for a given audit report ID. Enforces ownership: returns 404 if the report belongs to another user.")
    public ResponseEntity<AuditReportResponse> getReportById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        Profile profile = resolveUserProfile(user.getId());

        AuditReport report = auditReportRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Audit report not found with id: " + id));

        return ResponseEntity.ok(mapToDetailedResponse(report));
    }

    private User resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));
    }

    private Profile resolveUserProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for current user. Please create a profile first."));
    }

    private AuditReportSummaryResponse mapToSummaryResponse(AuditReport report) {
        return AuditReportSummaryResponse.builder()
                .id(report.getId())
                .overallScore(report.getOverallScore())
                .requestType(report.getRequestType())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private AuditReportResponse mapToDetailedResponse(AuditReport report) {
        return AuditReportResponse.builder()
                .id(report.getId())
                .profileId(report.getProfile() != null ? report.getProfile().getId() : null)
                .overallScore(report.getOverallScore())
                .sectionScores(report.getSectionScores())
                .weaknesses(report.getWeaknesses())
                .recruiterFeedback(report.getRecruiterFeedback())
                .requestType(report.getRequestType())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
