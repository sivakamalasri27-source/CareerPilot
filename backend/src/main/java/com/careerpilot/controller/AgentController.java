package com.careerpilot.controller;

import com.careerpilot.agent.dto.FullAuditResponse;
import com.careerpilot.agent.dto.RecruiterSimulationResponse;
import com.careerpilot.agent.orchestrator.AgentOrchestrator;
import com.careerpilot.dto.agent.AuditRequest;
import com.careerpilot.dto.agent.RecruiterSimulationRequest;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Tag(name = "AI Agents", description = "Endpoints for triggering multi-agent profile coaching and recruiter simulations")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @PostMapping("/audit")
    @Transactional
    @Operation(summary = "Run full multi-agent profile audit",
            description = "Triggers ProfileAuditorAgent and RecruiterSimulatorAgent in parallel. Saves audit report and returns scored breakdown.")
    public ResponseEntity<FullAuditResponse> runFullAudit(
            @RequestBody(required = false) AuditRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        Profile profile = resolveUserProfile(user);

        String targetRole = request != null ? request.getTargetRole() : null;
        FullAuditResponse response = agentOrchestrator.executeFullAudit(user, profile, targetRole);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recruiter-simulation")
    @Transactional
    @Operation(summary = "Run recruiter panel simulation",
            description = "Simulates hiring impressions and decisions from MNC HR, Startup Founder, and Technical Lead personas.")
    public ResponseEntity<RecruiterSimulationResponse> runRecruiterSimulation(
            @RequestBody(required = false) RecruiterSimulationRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        Profile profile = resolveUserProfile(user);

        String targetRole = request != null ? request.getTargetRole() : null;
        RecruiterSimulationResponse response = agentOrchestrator.executeRecruiterView(user, profile, targetRole);
        return ResponseEntity.ok(response);
    }

    private User resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));
    }

    private Profile resolveUserProfile(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(Profile.builder().user(user).build()));
    }
}
