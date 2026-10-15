package com.careerpilot.agent.recruiter;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.PersonaFeedback;
import com.careerpilot.agent.dto.PersonaType;
import com.careerpilot.agent.dto.RecruiterDecision;
import com.careerpilot.agent.dto.RecruiterSimulationResult;
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
public class RecruiterSimulatorAgent implements Agent<String, RecruiterSimulationResult> {

    public static final String AGENT_NAME = "RecruiterSimulatorAgent";
    private static final String PROMPT_FILE = "recruiter.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<RecruiterSimulationResult> execute(AgentContext<String> ctx) {
        log.info("Executing RecruiterSimulatorAgent for user ID: {}", ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        String targetRole = resolveTargetRole(ctx);
        String formattedProfile = (ctx.getInput() != null && !ctx.getInput().isBlank())
                ? ctx.getInput()
                : ProfileFormatter.formatProfileForAi(ctx.getProfile());

        Map<String, String> variables = new HashMap<>();
        variables.put("targetRole", targetRole);

        RecruiterSimulationResult fallback = createFallbackSimulation();

        try {
            RecruiterSimulationResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    formattedProfile,
                    RecruiterSimulationResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Simulation completed using fallback due to AI response constraints.");
            }
            return AgentResult.success(result);
        } catch (Exception e) {
            log.error("RecruiterSimulatorAgent execution failed: {}", e.getMessage(), e);
            return AgentResult.failure("Recruiter simulation failed: " + e.getMessage());
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

    private RecruiterSimulationResult createFallbackSimulation() {
        return RecruiterSimulationResult.builder()
                .consensusDecision(RecruiterDecision.MAYBE)
                .summary("Candidate shows foundation but requires clearer project demonstration and portfolio links.")
                .personas(List.of(
                        PersonaFeedback.builder()
                                .persona(PersonaType.MNC_HR)
                                .personaTitle("MNC HR Recruiter")
                                .firstImpression10Sec("Profile meets basic educational prerequisites.")
                                .strengths(List.of("Relevant degree and declared coursework."))
                                .redFlags(List.of("Lacks formal internship or certification records."))
                                .decision(RecruiterDecision.MAYBE)
                                .reasoning("Meets initial screening criteria but needs closer interview evaluation.")
                                .build(),
                        PersonaFeedback.builder()
                                .persona(PersonaType.STARTUP_FOUNDER)
                                .personaTitle("Startup Founder")
                                .firstImpression10Sec("Has projects listed but lacks live demos.")
                                .strengths(List.of("Self-driven project work."))
                                .redFlags(List.of("No verifiable deployment links."))
                                .decision(RecruiterDecision.MAYBE)
                                .reasoning("Would like to see deployed applications before extending an offer.")
                                .build(),
                        PersonaFeedback.builder()
                                .persona(PersonaType.TECH_LEAD)
                                .personaTitle("Technical Lead")
                                .firstImpression10Sec("Standard tech stack declared.")
                                .strengths(List.of("Modern language and framework awareness."))
                                .redFlags(List.of("No architectural metrics or test coverage specified."))
                                .decision(RecruiterDecision.MAYBE)
                                .reasoning("Code evidence needs review during technical assessment.")
                                .build()
                ))
                .build();
    }
}
