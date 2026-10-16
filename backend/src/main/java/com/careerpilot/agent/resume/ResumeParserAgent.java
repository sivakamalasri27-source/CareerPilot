package com.careerpilot.agent.resume;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.ParsedResumeResult;
import com.careerpilot.ai.service.AiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParserAgent implements Agent<String, ParsedResumeResult> {

    public static final String AGENT_NAME = "ResumeParserAgent";
    private static final String PROMPT_FILE = "resume_parser.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<ParsedResumeResult> execute(AgentContext<String> ctx) {
        log.info("Executing ResumeParserAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        String resumeText = (ctx.getInput() != null && !ctx.getInput().isBlank())
                ? ctx.getInput()
                : "";

        if (resumeText.isBlank()) {
            return AgentResult.failure("Cannot parse empty resume text.");
        }

        Map<String, String> variables = new HashMap<>();

        ParsedResumeResult fallback = ParsedResumeResult.builder()
                .skills(Collections.emptyList())
                .projects(Collections.emptyList())
                .education(Collections.emptyList())
                .build();

        try {
            ParsedResumeResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    resumeText,
                    ParsedResumeResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Resume parsed using safe fallback defaults.");
            }

            return AgentResult.success(result);
        } catch (Exception e) {
            log.error("ResumeParserAgent execution failed: {}", e.getMessage(), e);
            return AgentResult.fallback(fallback, "Fallback applied due to parser error: " + e.getMessage());
        }
    }
}
