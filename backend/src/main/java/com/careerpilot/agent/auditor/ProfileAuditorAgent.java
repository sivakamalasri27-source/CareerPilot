package com.careerpilot.agent.auditor;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.ProfileAuditResult;
import com.careerpilot.agent.dto.ProfileWeakness;
import com.careerpilot.agent.dto.SectionScores;
import com.careerpilot.agent.util.ProfileFormatter;
import com.careerpilot.ai.service.AiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileAuditorAgent implements Agent<String, ProfileAuditResult> {

    public static final String AGENT_NAME = "ProfileAuditorAgent";
    private static final String PROMPT_FILE = "auditor.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<ProfileAuditResult> execute(AgentContext<String> ctx) {
        log.info("Executing ProfileAuditorAgent for user ID: {}", ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        String targetRole = resolveTargetRole(ctx);
        String formattedProfile = (ctx.getInput() != null && !ctx.getInput().isBlank())
                ? ctx.getInput()
                : ProfileFormatter.formatProfileForAi(ctx.getProfile());

        Map<String, String> variables = new HashMap<>();
        variables.put("targetRole", targetRole);

        ProfileAuditResult fallback = createFallbackAudit();

        try {
            ProfileAuditResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    formattedProfile,
                    ProfileAuditResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Audit completed using fallback due to AI response constraints.");
            }
            return AgentResult.success(result);
        } catch (Exception e) {
            log.error("ProfileAuditorAgent execution failed: {}", e.getMessage(), e);
            return AgentResult.failure("Auditor failed: " + e.getMessage());
        }
    }

    private String resolveTargetRole(AgentContext<String> ctx) {
        if (ctx.getTargetRole() != null && !ctx.getTargetRole().isBlank()) {
            return ctx.getTargetRole().trim();
        }
        if (ctx.getProfile() != null && ctx.getProfile().getTargetRole() != null && !ctx.getProfile().getTargetRole().isBlank()) {
            return ctx.getProfile().getTargetRole().trim();
        }
        return "Software Engineer";
    }

    private ProfileAuditResult createFallbackAudit() {
        return ProfileAuditResult.builder()
                .overallScore(50)
                .sectionScores(SectionScores.builder()
                        .headline(50)
                        .about(50)
                        .skills(50)
                        .projects(50)
                        .education(50)
                        .build())
                .weaknesses(List.of(
                        ProfileWeakness.builder()
                                .section("HEADLINE")
                                .weakText("Profile headline")
                                .reason("Automated review was unable to analyze headline metrics.")
                                .improvementSuggestion("Review and refine your headline manually with key skills.")
                                .build()
                ))
                .strengths(List.of("Basic profile structure is in place."))
                .keyRecommendations(List.of(
                        "Add quantifiable metrics to project descriptions.",
                        "Verify and link public GitHub repositories."
                ))
                .build();
    }
}
