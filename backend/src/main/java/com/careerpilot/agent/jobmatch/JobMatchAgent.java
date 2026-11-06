package com.careerpilot.agent.jobmatch;

import com.careerpilot.agent.core.Agent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.util.ProfileFormatter;
import com.careerpilot.ai.prompt.PromptSanitizer;
import com.careerpilot.ai.service.AiExecutionService;
import com.careerpilot.dto.jobmatch.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchAgent implements Agent<JobMatchComputationResult, JobMatchResult> {

    public static final String AGENT_NAME = "JobMatchAgent";
    private static final String PROMPT_FILE = "job_match.v1.txt";

    private final AiExecutionService aiExecutionService;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentResult<JobMatchResult> execute(AgentContext<JobMatchComputationResult> ctx) {
        log.info("Executing JobMatchAgent for user ID: {}",
                ctx.getUser() != null ? ctx.getUser().getId() : "anonymous");

        JobMatchComputationResult computation = ctx.getInput();
        if (computation == null) {
            return AgentResult.failure("Job match computation data is required");
        }

        String formattedProfile = ProfileFormatter.formatProfileForAi(ctx.getProfile());

        Map<String, String> variables = new HashMap<>();
        variables.put("jobTitle", computation.getJobTitle() != null && !computation.getJobTitle().isBlank()
                ? computation.getJobTitle()
                : "Software Engineer");
        variables.put("companyName", computation.getCompanyName() != null && !computation.getCompanyName().isBlank()
                ? computation.getCompanyName()
                : "Target Company");
        variables.put("matchPercentage", String.format(Locale.US, "%.1f",
                computation.getMatchPercentage() != null ? computation.getMatchPercentage() : 0.0));
        variables.put("extractedExperience", computation.getExtractedExperience() != null
                ? computation.getExtractedExperience()
                : "Not specified");
        variables.put("matchedSkills", formatMatchedSkills(computation.getMatchedSkills()));
        variables.put("missingSkills", formatMissingSkills(computation.getMissingSkills()));
        variables.put("jobDescriptionData", PromptSanitizer.wrapJobDescription(computation.getJobDescription()));

        JobMatchResult fallback = buildFallbackResult(computation);

        try {
            JobMatchResult result = aiExecutionService.execute(
                    ctx.getUser(),
                    getName(),
                    PROMPT_FILE,
                    variables,
                    formattedProfile,
                    JobMatchResult.class,
                    () -> fallback
            );

            if (result == fallback) {
                return AgentResult.fallback(fallback, "Job match generated using deterministic fallback.");
            }

            // Ensure 3 profile tweaks exist
            normalizeProfileTweaks(result, fallback);

            return AgentResult.success(result);
        } catch (Exception e) {
            log.warn("JobMatchAgent execution failed, falling back to deterministic result: {}", e.getMessage());
            return AgentResult.fallback(fallback, "Deterministic job match preserved: " + e.getMessage());
        }
    }

    private JobMatchResult buildFallbackResult(JobMatchComputationResult computation) {
        String topMatched = (computation.getMatchedSkills() != null && !computation.getMatchedSkills().isEmpty())
                ? computation.getMatchedSkills().get(0).getSkillName()
                : "primary technical skills";

        List<String> tweaks = new ArrayList<>(List.of(
                "Feature your " + topMatched + " projects and claimed achievements prominently in your headline and summary to align with the core job requirements.",
                "Detail quantifiable outcomes, system architecture, and unit test coverage in your project bullets to demonstrate production readiness for this role.",
                "Review the missing skills (" + formatMissingSkillNames(computation.getMissingSkills()) + ") and highlight relevant coursework or fast-learning initiatives."
        ));

        List<String> topics = new ArrayList<>();
        if (computation.getMatchedSkills() != null) {
            for (JobMatchedSkillDto s : computation.getMatchedSkills()) {
                if (topics.size() < 4) {
                    topics.add(s.getSkillName() + " core fundamentals, architectural patterns, and production trade-offs");
                }
            }
        }
        if (topics.size() < 3) {
            topics.add("System design principles, RESTful API architecture, and database query optimization");
            topics.add("Clean code principles, unit testing with JUnit/Mockito, and automated CI/CD pipelines");
        }

        String summary = String.format(Locale.US,
                "Candidate has %.1f%% alignment with this role. Matches %d required skills and has %d skill gaps to address.",
                computation.getMatchPercentage() != null ? computation.getMatchPercentage() : 0.0,
                computation.getMatchedSkills() != null ? computation.getMatchedSkills().size() : 0,
                computation.getMissingSkills() != null ? computation.getMissingSkills().size() : 0
        );

        return JobMatchResult.builder()
                .fitSummary(summary)
                .profileTweaks(tweaks)
                .interviewTopics(topics)
                .build();
    }

    private void normalizeProfileTweaks(JobMatchResult result, JobMatchResult fallback) {
        if (result.getProfileTweaks() == null || result.getProfileTweaks().isEmpty()) {
            result.setProfileTweaks(fallback.getProfileTweaks());
        } else if (result.getProfileTweaks().size() < 3) {
            List<String> list = new ArrayList<>(result.getProfileTweaks());
            for (String fallbackTweak : fallback.getProfileTweaks()) {
                if (list.size() >= 3) break;
                if (!list.contains(fallbackTweak)) {
                    list.add(fallbackTweak);
                }
            }
            result.setProfileTweaks(list);
        } else if (result.getProfileTweaks().size() > 3) {
            result.setProfileTweaks(new ArrayList<>(result.getProfileTweaks().subList(0, 3)));
        }
    }

    private String formatMatchedSkills(List<JobMatchedSkillDto> skills) {
        if (skills == null || skills.isEmpty()) {
            return "None identified";
        }
        StringBuilder sb = new StringBuilder();
        for (JobMatchedSkillDto s : skills) {
            sb.append("- ").append(s.getSkillName())
                    .append(" (Claimed: ").append(s.getClaimedLevel())
                    .append(", Verification: ").append(s.getVerificationStatus()).append(")\n");
        }
        return sb.toString().trim();
    }

    private String formatMissingSkills(List<JobMissingSkillDto> skills) {
        if (skills == null || skills.isEmpty()) {
            return "None identified";
        }
        StringBuilder sb = new StringBuilder();
        for (JobMissingSkillDto s : skills) {
            sb.append("- ").append(s.getSkillName())
                    .append(" (Category: ").append(s.getCategory() != null ? s.getCategory() : "General").append(")\n");
        }
        return sb.toString().trim();
    }

    private String formatMissingSkillNames(List<JobMissingSkillDto> skills) {
        if (skills == null || skills.isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        for (JobMissingSkillDto s : skills) {
            names.add(s.getSkillName());
            if (names.size() >= 3) break;
        }
        return String.join(", ", names);
    }
}
