package com.careerpilot.agent.coach;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.CoachTaskDto;
import com.careerpilot.agent.dto.WeeklyCoachResult;
import com.careerpilot.ai.service.AiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyCoachAgent implements Agent<String, WeeklyCoachResult> {

    public static final String AGENT_NAME = "WeeklyCoachAgent";
    private static final String PROMPT_FILE = "weekly_coach.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<WeeklyCoachResult> execute(AgentContext<String> ctx) {
        log.info("Executing WeeklyCoachAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        String contextData = (ctx.getInput() != null && !ctx.getInput().isBlank())
                ? ctx.getInput()
                : "No past audit report or skill gap analysis available. Candidate is seeking foundational fresher improvement steps.";

        Map<String, String> variables = new HashMap<>();

        WeeklyCoachResult fallback = buildFallbackResult(contextData);

        try {
            WeeklyCoachResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    contextData,
                    WeeklyCoachResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Generated foundational weekly action tasks using starter curriculum.");
            }

            // Ensure exactly 3 tasks
            if (result.getTasks() == null || result.getTasks().size() != 3) {
                log.warn("Model returned {} tasks instead of exactly 3. Adjusting with fallback.",
                        result.getTasks() != null ? result.getTasks().size() : 0);
                return AgentResult.fallback(fallback, "Adjusted weekly plan to satisfy exact 3-task contract.");
            }

            return AgentResult.success(result);
        } catch (Exception e) {
            log.error("WeeklyCoachAgent execution failed, returning fallback: {}", e.getMessage(), e);
            return AgentResult.fallback(fallback, "Generated foundational weekly plan: " + e.getMessage());
        }
    }

    private WeeklyCoachResult buildFallbackResult(String contextData) {
        List<CoachTaskDto> defaultTasks = List.of(
                CoachTaskDto.builder()
                        .title("Configure GitHub Profile README with Technical Portfolio")
                        .description("Create a personalized GitHub profile repository (README.md) displaying tech stack badges, target role, and links to top 2 repositories.")
                        .category("PROFILE_OPTIMIZATION")
                        .build(),
                CoachTaskDto.builder()
                        .title("Refactor Project Bullet Points into STAR/XYZ Format")
                        .description("Rewrite at least 3 project bullets using Action Verb + What Was Built + Technology + Measurable Latency or Coverage Metric.")
                        .category("PROJECT_IMPROVEMENT")
                        .build(),
                CoachTaskDto.builder()
                        .title("Implement Automated Unit Tests Achieving >80% Coverage")
                        .description("Add JUnit 5 and Mockito test classes covering edge cases and exception handlers in your primary backend repository.")
                        .category("SKILL_BUILDING")
                        .build()
        );

        return WeeklyCoachResult.builder()
                .tasks(defaultTasks)
                .focusArea("Foundational Software Engineering Readiness")
                .rationale("Essential starter milestones to establish an industry-ready fresher profile and verified code presence.")
                .build();
    }
}
