package com.careerpilot.agent.skill;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.SkillVerificationResult;
import com.careerpilot.ai.service.AiExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProofOfSkillAgent implements Agent<SkillVerificationResult, SkillVerificationResult> {

    public static final String AGENT_NAME = "ProofOfSkillAgent";
    private static final String PROMPT_FILE = "proof.v1.txt";

    private final AiExecutionService aiExecutionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<SkillVerificationResult> execute(AgentContext<SkillVerificationResult> ctx) {
        log.info("Executing ProofOfSkillAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        SkillVerificationResult precomputed = ctx.getInput();
        if (precomputed == null || precomputed.getSkills() == null || precomputed.getSkills().isEmpty()) {
            SkillVerificationResult emptyResult = SkillVerificationResult.builder()
                    .overallVerifiedPercentage(0.0)
                    .skills(Collections.emptyList())
                    .summary("No claimed skills to verify.")
                    .build();
            return AgentResult.success(emptyResult);
        }

        String serializedEvidence;
        try {
            serializedEvidence = objectMapper.writeValueAsString(precomputed);
        } catch (Exception e) {
            log.warn("Failed to serialize skill evidence, using toString: {}", e.getMessage());
            serializedEvidence = precomputed.toString();
        }

        Map<String, String> variables = new HashMap<>();

        try {
            SkillVerificationResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    serializedEvidence,
                    SkillVerificationResult.class,
                    () -> precomputed
            );

            if (result == precomputed) {
                return AgentResult.fallback(precomputed, "Skill verification completed using deterministic Java matcher.");
            }

            return AgentResult.success(result);
        } catch (Exception e) {
            log.warn("ProofOfSkillAgent execution error, returning Java matched result: {}", e.getMessage());
            return AgentResult.fallback(precomputed, "Deterministic verification preserved: " + e.getMessage());
        }
    }
}
