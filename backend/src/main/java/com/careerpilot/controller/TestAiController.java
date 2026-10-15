package com.careerpilot.controller;

import com.careerpilot.ai.dto.SampleAuditDto;
import com.careerpilot.ai.service.AiExecutionService;
import com.careerpilot.dto.ai.TestAiAuditRequest;
import com.careerpilot.entity.User;
import com.careerpilot.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/ai")
@RequiredArgsConstructor
@Tag(name = "AI Foundation Test", description = "Endpoints for testing and validating the AI pipeline")
@SecurityRequirement(name = "bearerAuth")
public class TestAiController {

    private final AiExecutionService aiExecutionService;
    private final UserRepository userRepository;

    @PostMapping("/execute")
    @Operation(summary = "Execute test AI audit pipeline", description = "Executes an AI audit through rate-limiting, caching, injection defense, validation, and usage logging.")
    public ResponseEntity<SampleAuditDto> executeTestAudit(
            @Valid @RequestBody TestAiAuditRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = null;
        if (userDetails != null) {
            user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("targetRole", request.getTargetRole());

        SampleAuditDto fallback = SampleAuditDto.builder()
                .score(50)
                .summary("Fallback audit generated due to AI processing constraints.")
                .recommendations(List.of("Review profile manually", "Add GitHub repositories"))
                .missingFields(List.of("Manual review required"))
                .build();

        SampleAuditDto result = aiExecutionService.execute(
                user,
                "TestAuditor",
                "sample-audit.v1.txt",
                variables,
                request.getCandidateData(),
                SampleAuditDto.class,
                () -> fallback
        );

        return ResponseEntity.ok(result);
    }
}
