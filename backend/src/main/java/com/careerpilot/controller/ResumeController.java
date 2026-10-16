package com.careerpilot.controller;

import com.careerpilot.dto.resume.ResumeUploadResponse;
import com.careerpilot.dto.resume.ResumeUploadSummaryResponse;
import com.careerpilot.entity.User;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
@Tag(name = "Resume Management", description = "Endpoints for uploading PDF resumes, in-memory parsing, suggestion generation, and upload history")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and parse PDF resume",
            description = "Uploads a PDF resume (max 5 MB), extracts text in memory with Apache PDFBox, and extracts structured items into PENDING suggestions (skipping duplicates). Never stores the file on disk.")
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        ResumeUploadResponse response = resumeService.uploadAndProcessResume(user, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get resume upload history",
            description = "Retrieves the list of past resume uploads for the authenticated user.")
    public ResponseEntity<List<ResumeUploadSummaryResponse>> getResumeHistory(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        List<ResumeUploadSummaryResponse> history = resumeService.getResumeUploadHistory(user);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete resume upload record",
            description = "Deletes a resume upload record. Strictly restricted to owner; returns 404 if not found or belongs to another user.")
    public ResponseEntity<Void> deleteResume(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        resumeService.deleteResumeUpload(user, id);
        return ResponseEntity.noContent().build();
    }

    private User resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));
    }
}
