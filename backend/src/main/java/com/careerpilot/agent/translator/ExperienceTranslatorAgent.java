package com.careerpilot.agent.translator;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.ExperienceTranslationResult;
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
public class ExperienceTranslatorAgent implements Agent<String, ExperienceTranslationResult> {

    public static final String AGENT_NAME = "ExperienceTranslatorAgent";
    private static final String PROMPT_FILE = "translator.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<ExperienceTranslationResult> execute(AgentContext<String> ctx) {
        log.info("Executing ExperienceTranslatorAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        String targetRole = resolveTargetRole(ctx);
        String experienceData = (ctx.getInput() != null && !ctx.getInput().isBlank())
                ? ctx.getInput()
                : "No project experience details provided.";

        Map<String, String> variables = new HashMap<>();
        variables.put("targetRole", targetRole);

        ExperienceTranslationResult fallback = createFallbackTranslation();

        try {
            ExperienceTranslationResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    experienceData,
                    ExperienceTranslationResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Experience translation completed using safe fallback.");
            }

            return AgentResult.success(result);
        } catch (Exception e) {
            log.error("ExperienceTranslatorAgent execution failed: {}", e.getMessage(), e);
            return AgentResult.fallback(fallback, "Fallback applied due to error: " + e.getMessage());
        }
    }

    private String resolveTargetRole(AgentContext<String> ctx) {
        if (ctx.getTargetRole() != null && !ctx.getTargetRole().isBlank()) {
            return ctx.getTargetRole();
        }
        if (ctx.getProfile() != null && ctx.getProfile().getTargetRole() != null && !ctx.getProfile().getTargetRole().isBlank()) {
            return ctx.getProfile().getTargetRole();
        }
        return "Software Engineer";
    }

    private ExperienceTranslationResult createFallbackTranslation() {
        return ExperienceTranslationResult.builder()
                .bullets(List.of(
                        "Developed core software features and RESTful endpoints using designated stack",
                        "Structured modular application logic and configured relational database schemas"
                ))
                .clarifyingQuestions(List.of(
                        "What was the estimated user traffic, request volume, or database size?",
                        "Did you measure automated test coverage or deployment latency improvements?"
                ))
                .rationale("Safe fallback generated without fabricated metrics or unverified numbers.")
                .build();
    }
}
