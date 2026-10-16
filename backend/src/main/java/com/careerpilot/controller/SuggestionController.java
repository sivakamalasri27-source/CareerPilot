package com.careerpilot.controller;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.SuggestionStatus;
import com.careerpilot.entity.User;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
@Tag(name = "Agent Suggestions", description = "Human-in-the-loop suggestion management, review, edit, approve, and reject workflows")
@SecurityRequirement(name = "bearerAuth")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List suggestions for authenticated user",
            description = "Returns paginated list of suggestions, sorted newest first by default. Filterable by status (e.g. PENDING).")
    public ResponseEntity<PageResponse<SuggestionResponse>> getSuggestions(
            @RequestParam(required = false, defaultValue = "PENDING") SuggestionStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        PageResponse<SuggestionResponse> response = suggestionService.getSuggestions(user, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get suggestion details by ID",
            description = "Retrieves an individual agent suggestion. Returns 404 if not found or belongs to another user.")
    public ResponseEntity<SuggestionResponse> getSuggestionById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        SuggestionResponse response = suggestionService.getSuggestionById(user, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve an agent suggestion",
            description = "Applies the suggested or edited text to the profile or target project in the same transaction, then marks the suggestion APPROVED. Validates against stale target data.")
    public ResponseEntity<SuggestionResponse> approveSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveSuggestionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        SuggestionResponse response = suggestionService.approveSuggestion(user, id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject an agent suggestion",
            description = "Marks the suggestion REJECTED without modifying any profile or project data.")
    public ResponseEntity<SuggestionResponse> rejectSuggestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        SuggestionResponse response = suggestionService.rejectSuggestion(user, id);
        return ResponseEntity.ok(response);
    }

    private User resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));
    }
}
