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
                        .description("Create a personalized GitHub profile README displaying tech stack badges, target role, and links to top 2 repositories.")
                        .category("PROFILE_OPTIMIZATION")
                        .estimatedTime("~1 hour")
                        .whyThisMatters("Gives recruiters an immediate snapshot of your tech stack and top projects.")
                        .verificationType("GITHUB_FILE_EXISTS")
                        .verificationHint("README.md")
                        .build(),
                CoachTaskDto.builder()
                        .title("Refactor Project Bullet Points into STAR Format")
                        .description("Rewrite at least 3 project bullets using Action Verb + What Was Built + Technology + Key Result.")
                        .category("PROJECT_IMPROVEMENT")
                        .estimatedTime("~1 hour")
                        .whyThisMatters("STAR bullets communicate impact clearly and perform better in recruiter screens.")
                        .verificationType("PROFILE_CHANGE")
                        .verificationHint("projects")
                        .build(),
                CoachTaskDto.builder()
                        .title("Write Unit Tests for One Core Service Class")
                        .description("Add JUnit 5 and Mockito test methods covering happy path and edge cases in your primary service class.")
                        .category("SKILL_BUILDING")
                        .estimatedTime("~1-2 hours")
                        .whyThisMatters("Demonstrates code reliability and automated testing skills expected of freshers.")
                        .verificationType("GITHUB_RECENT_COMMIT")
                        .verificationHint(null)
                        .build()
        );

        return WeeklyCoachResult.builder()
                .tasks(defaultTasks)
                .focusArea("Foundational Software Engineering Readiness")
                .rationale("Essential starter milestones to establish an industry-ready fresher profile and verified code presence.")
                .build();
    }
}
