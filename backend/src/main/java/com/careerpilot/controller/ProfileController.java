package com.careerpilot.controller;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.profile.*;
import com.careerpilot.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile & Portfolio", description = "Endpoints for managing candidate profile, skills, projects, and education")
@SecurityRequirement(name = "BearerAuth")
public class ProfileController {

    private final ProfileService profileService;

    // =========================================================================
    // Profile
    // =========================================================================

    @GetMapping
    @Operation(summary = "Get current candidate profile", description = "Retrieves or auto-creates the profile of the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        ProfileResponse response = profileService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(summary = "Update candidate profile", description = "Updates headline, about, and links with optimistic locking check")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking conflict")
    })
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProfileRequest request) {
        ProfileResponse response = profileService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Projects
    // =========================================================================

    @GetMapping("/projects")
    @Operation(summary = "List candidate projects", description = "Paginated list of projects belonging to the authenticated user")
    public ResponseEntity<PageResponse<ProjectResponse>> getProjects(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProjectResponse> response = profileService.getProjects(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Get project by ID", description = "Returns project details; returns 404 if project does not belong to user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<ProjectResponse> getProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        ProjectResponse response = profileService.getProject(userDetails.getUsername(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/projects")
    @Operation(summary = "Add a new project", description = "Adds a coursework or independent project with JSON achievement bullets")
    @ApiResponse(responseCode = "201", description = "Project created")
    public ResponseEntity<ProjectResponse> addProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProjectRequest request) {
        ProjectResponse response = profileService.addProject(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/projects/{id}")
    @Operation(summary = "Update project", description = "Updates project details; returns 404 if project does not belong to user")
    public ResponseEntity<ProjectResponse> updateProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request) {
        ProjectResponse response = profileService.updateProject(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/projects/{id}")
    @Operation(summary = "Delete project", description = "Deletes project; returns 404 if project does not belong to user")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        profileService.deleteProject(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Education
    // =========================================================================

    @GetMapping("/education")
    @Operation(summary = "List candidate education records", description = "Paginated list of education entries for authenticated user")
    public ResponseEntity<PageResponse<EducationResponse>> getEducation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<EducationResponse> response = profileService.getEducation(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/education/{id}")
    @Operation(summary = "Get education by ID", description = "Returns education details; returns 404 if not found or not owned")
    public ResponseEntity<EducationResponse> getEducationById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        EducationResponse response = profileService.getEducationById(userDetails.getUsername(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/education")
    @Operation(summary = "Add education record", description = "Adds degree, college, GPA, and extracurricular activities")
    @ApiResponse(responseCode = "201", description = "Education created")
    public ResponseEntity<EducationResponse> addEducation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EducationRequest request) {
        EducationResponse response = profileService.addEducation(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/education/{id}")
    @Operation(summary = "Update education record", description = "Updates education record; returns 404 if not owned")
    public ResponseEntity<EducationResponse> updateEducation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody EducationRequest request) {
        EducationResponse response = profileService.updateEducation(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/education/{id}")
    @Operation(summary = "Delete education record", description = "Deletes education record; returns 404 if not owned")
    public ResponseEntity<Void> deleteEducation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        profileService.deleteEducation(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Skills
    // =========================================================================

    @GetMapping("/skills")
    @Operation(summary = "List candidate skills", description = "Paginated list of skills claimed by the authenticated user")
    public ResponseEntity<PageResponse<ProfileSkillResponse>> getSkills(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProfileSkillResponse> response = profileService.getSkills(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/skills")
    @Operation(summary = "Add skill to profile", description = "Links a skill from shared catalog to user profile with proficiency level")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Skill added"),
            @ApiResponse(responseCode = "400", description = "Exceeded max 50 skills"),
            @ApiResponse(responseCode = "409", description = "Skill already linked to profile")
    })
    public ResponseEntity<ProfileSkillResponse> addSkill(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProfileSkillRequest request) {
        ProfileSkillResponse response = profileService.addSkill(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/skills/{id}")
    @Operation(summary = "Update skill proficiency level", description = "Updates claimed level; returns 404 if not owned")
    public ResponseEntity<ProfileSkillResponse> updateSkill(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProfileSkillUpdateRequest request) {
        ProfileSkillResponse response = profileService.updateSkill(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/skills/{id}")
    @Operation(summary = "Remove skill from profile", description = "Removes skill link; returns 404 if not owned")
    public ResponseEntity<Void> deleteSkill(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        profileService.deleteSkill(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
