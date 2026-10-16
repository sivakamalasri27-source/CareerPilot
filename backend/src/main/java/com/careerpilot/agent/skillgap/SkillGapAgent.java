package com.careerpilot.agent.skillgap;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.SkillGapResult;
import com.careerpilot.ai.service.AiExecutionService;
import com.careerpilot.dto.skillgap.MissingSkillDto;
import com.careerpilot.dto.skillgap.RoadmapItemDto;
import com.careerpilot.dto.skillgap.SkillGapComputationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillGapAgent implements Agent<SkillGapComputationResult, SkillGapResult> {

    public static final String AGENT_NAME = "SkillGapAgent";
    private static final String PROMPT_FILE = "skillgap.v1.txt";

    private final AiExecutionService aiExecutionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<SkillGapResult> execute(AgentContext<SkillGapComputationResult> ctx) {
        log.info("Executing SkillGapAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        SkillGapComputationResult computation = ctx.getInput();
        if (computation == null) {
            return AgentResult.failure("Skill gap computation data is required");
        }

        String serializedComputation;
        try {
            serializedComputation = objectMapper.writeValueAsString(computation);
        } catch (Exception e) {
            log.warn("Failed to serialize skill gap computation, using toString: {}", e.getMessage());
            serializedComputation = computation.toString();
        }

        Map<String, String> variables = new HashMap<>();

        SkillGapResult fallback = buildFallbackResult(computation);

        try {
            SkillGapResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    serializedComputation,
                    SkillGapResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Skill gap roadmap fallback generated using standard curriculum defaults.");
            }

            // Ensure matched/missing/weak from deterministic calculation are preserved
            result.setRoleId(computation.getRoleId());
            result.setRoleName(computation.getRoleName());
            result.setMatchPercentage(computation.getMatchPercentage());
            result.setMatchedSkills(computation.getMatchedSkills());
            result.setMissingSkills(computation.getMissingSkills());
            result.setWeakSkills(computation.getWeakSkills());

            return AgentResult.success(result);
        } catch (Exception e) {
            log.warn("SkillGapAgent execution failed, falling back to deterministic result: {}", e.getMessage());
            return AgentResult.fallback(fallback, "Deterministic gap analysis preserved: " + e.getMessage());
        }
    }

    private SkillGapResult buildFallbackResult(SkillGapComputationResult computation) {
        List<RoadmapItemDto> fallbackRoadmap = new ArrayList<>();
        if (computation.getMissingSkills() != null) {
            for (MissingSkillDto missing : computation.getMissingSkills()) {
                fallbackRoadmap.add(RoadmapItemDto.builder()
                        .skillName(missing.getSkillName())
                        .priority(missing.getPriority())
                        .whyItMatters("Essential requirement for " + computation.getRoleName() + " in industry engineering teams.")
                        .whatToLearn("Core fundamentals, official documentation, and best practices for " + missing.getSkillName() + ".")
                        .smallProjectIdea("Build a small GitHub repository demonstrating end-to-end integration of " + missing.getSkillName() + ".")
                        .build());
            }
        }

        String summary = String.format("Calculated %.1f%% alignment with %s. Prioritize %d missing skills to reach full proficiency.",
                computation.getMatchPercentage(),
                computation.getRoleName(),
                computation.getMissingSkills() != null ? computation.getMissingSkills().size() : 0);

        return SkillGapResult.builder()
                .roleId(computation.getRoleId())
                .roleName(computation.getRoleName())
                .matchPercentage(computation.getMatchPercentage())
                .matchedSkills(computation.getMatchedSkills())
                .missingSkills(computation.getMissingSkills())
                .weakSkills(computation.getWeakSkills())
                .learningRoadmap(fallbackRoadmap)
                .summary(summary)
                .build();
    }
}
