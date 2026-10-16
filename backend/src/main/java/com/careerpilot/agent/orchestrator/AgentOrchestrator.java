package com.careerpilot.agent.orchestrator;

import com.careerpilot.agent.auditor.ProfileAuditorAgent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.*;
import com.careerpilot.agent.recruiter.RecruiterSimulatorAgent;
import com.careerpilot.agent.skill.ProofOfSkillAgent;
import com.careerpilot.agent.skill.SkillEvidenceMatcher;
import com.careerpilot.agent.translator.ExperienceTranslatorAgent;
import com.careerpilot.agent.util.ProfileFormatter;
import com.careerpilot.dto.agent.TranslateExperienceRequest;
import com.careerpilot.dto.agent.TranslateExperienceResponse;
import com.careerpilot.dto.agent.VerifySkillsResponse;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.*;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.AuditReportRepository;
import com.careerpilot.repository.ProjectRepository;
import com.careerpilot.service.SuggestionService;
import com.careerpilot.tool.github.GitHubRepoDto;
import com.careerpilot.tool.github.GitHubTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final long ORCHESTRATOR_TIMEOUT_SECONDS = 25;

    private final ProfileAuditorAgent profileAuditorAgent;
    private final RecruiterSimulatorAgent recruiterSimulatorAgent;
    private final ExperienceTranslatorAgent experienceTranslatorAgent;
    private final ProofOfSkillAgent proofOfSkillAgent;
    private final GitHubTool gitHubTool;
    private final SkillEvidenceMatcher skillEvidenceMatcher;
    private final AuditReportRepository auditReportRepository;
    private final ProjectRepository projectRepository;
    private final SuggestionService suggestionService;

    @Qualifier("orchestratorExecutor")
    private final Executor orchestratorExecutor;

    /**
     * Executes a full profile audit running ProfileAuditorAgent and RecruiterSimulatorAgent in parallel.
     * Tolerates partial failure and persists the result to audit_reports.
     */
    @Transactional
    public FullAuditResponse executeFullAudit(User user, Profile profile, String targetRole) {
        log.info("Orchestrating FULL_AUDIT for profile ID: {}", profile != null ? profile.getId() : null);

        // 1. Fail-fast validation: Reject empty or near-empty profiles with HTTP 400
        ProfileFormatter.validateNotEmpty(profile);

        // 2. Format profile on caller thread to prevent concurrent Hibernate session access
        String formattedProfile = ProfileFormatter.formatProfileForAi(profile);

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(user)
                .profile(profile)
                .input(formattedProfile)
                .targetRole(targetRole)
                .build();

        // 3. Launch ProfileAuditorAgent in parallel
        CompletableFuture<AgentResult<ProfileAuditResult>> auditorFuture = CompletableFuture.supplyAsync(
                () -> profileAuditorAgent.execute(ctx),
                orchestratorExecutor
        ).exceptionally(ex -> {
            log.error("ProfileAuditorAgent threw unhandled exception: {}", ex.getMessage(), ex);
            return AgentResult.failure("Auditor failed unexpectedly: " + ex.getMessage());
        });

        // 4. Launch RecruiterSimulatorAgent in parallel
        CompletableFuture<AgentResult<RecruiterSimulationResult>> recruiterFuture = CompletableFuture.supplyAsync(
                () -> recruiterSimulatorAgent.execute(ctx),
                orchestratorExecutor
        ).exceptionally(ex -> {
            log.error("RecruiterSimulatorAgent threw unhandled exception: {}", ex.getMessage(), ex);
            return AgentResult.failure("Recruiter simulation failed unexpectedly: " + ex.getMessage());
        });

        // 5. Wait for both futures to complete with bounded timeout
        try {
            CompletableFuture.allOf(auditorFuture, recruiterFuture)
                    .get(ORCHESTRATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Orchestrator timed out after {} seconds. Collecting completed agent results.", ORCHESTRATOR_TIMEOUT_SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Orchestrator thread interrupted", ie);
        } catch (ExecutionException ee) {
            log.warn("One or more agent futures completed exceptionally: {}", ee.getMessage());
        }

        AgentResult<ProfileAuditResult> auditorResult = resolveFutureWithFallback(
                auditorFuture,
                () -> AgentResult.failure("Auditor timed out or was cancelled")
        );

        AgentResult<RecruiterSimulationResult> recruiterResult = resolveFutureWithFallback(
                recruiterFuture,
                () -> AgentResult.failure("Recruiter simulation timed out or was cancelled")
        );

        // 6. Persist audit results to database
        AuditReport report = persistAuditReport(profile, auditorResult, recruiterResult, RequestType.FULL_AUDIT);

        // 7. Optionally create suggestions from auditor weaknesses for headline and about
        createSuggestionsFromWeaknesses(profile, auditorResult);

        return FullAuditResponse.builder()
                .reportId(report.getId())
                .requestType(RequestType.FULL_AUDIT)
                .auditorResult(auditorResult)
                .recruiterResult(recruiterResult)
                .createdAt(report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now())
                .build();
    }

    /**
     * Executes recruiter simulation for a profile.
     */
    public RecruiterSimulationResponse executeRecruiterView(User user, Profile profile, String targetRole) {
        log.info("Orchestrating RECRUITER_VIEW for profile ID: {}", profile != null ? profile.getId() : null);

        // 1. Fail-fast validation
        ProfileFormatter.validateNotEmpty(profile);

        // 2. Format profile on caller thread
        String formattedProfile = ProfileFormatter.formatProfileForAi(profile);

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(user)
                .profile(profile)
                .input(formattedProfile)
                .targetRole(targetRole)
                .build();

        // 3. Execute RecruiterSimulatorAgent
        CompletableFuture<AgentResult<RecruiterSimulationResult>> recruiterFuture = CompletableFuture.supplyAsync(
                () -> recruiterSimulatorAgent.execute(ctx),
                orchestratorExecutor
        ).exceptionally(ex -> {
            log.error("RecruiterSimulatorAgent threw unhandled exception: {}", ex.getMessage(), ex);
            return AgentResult.failure("Recruiter simulation failed unexpectedly: " + ex.getMessage());
        });

        try {
            recruiterFuture.get(ORCHESTRATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Recruiter view execution encountered issue: {}", e.getMessage());
        }

        AgentResult<RecruiterSimulationResult> recruiterResult = resolveFutureWithFallback(
                recruiterFuture,
                () -> AgentResult.failure("Recruiter simulation timed out or failed")
        );

        return RecruiterSimulationResponse.builder()
                .requestType(RequestType.RECRUITER_VIEW)
                .recruiterResult(recruiterResult)
                .createdAt(Instant.now())
                .build();
    }

    private <T> AgentResult<T> resolveFutureWithFallback(
            CompletableFuture<AgentResult<T>> future,
            java.util.function.Supplier<AgentResult<T>> timeoutFallbackSupplier
    ) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            try {
                return future.get();
            } catch (Exception e) {
                return timeoutFallbackSupplier.get();
            }
        }
        return timeoutFallbackSupplier.get();
    }

    private AuditReport persistAuditReport(
            Profile profile,
            AgentResult<ProfileAuditResult> auditorResult,
            AgentResult<RecruiterSimulationResult> recruiterResult,
            RequestType requestType
    ) {
        int overallScore = 50;
        Map<String, Object> sectionScores = new HashMap<>();
        List<Map<String, Object>> weaknesses = new ArrayList<>();

        if (auditorResult != null && auditorResult.getData() != null) {
            ProfileAuditResult audit = auditorResult.getData();
            if (audit.getOverallScore() != null) {
                overallScore = audit.getOverallScore();
            }
            if (audit.getSectionScores() != null) {
                sectionScores = audit.getSectionScores().toMap();
            }
            if (audit.getWeaknesses() != null) {
                for (ProfileWeakness w : audit.getWeaknesses()) {
                    weaknesses.add(w.toMap());
                }
            }
        }

        Map<String, Object> recruiterFeedback = null;
        if (recruiterResult != null && recruiterResult.getData() != null) {
            recruiterFeedback = recruiterResult.getData().toMap();
        }

        AuditReport report = AuditReport.builder()
                .profile(profile)
                .overallScore(overallScore)
                .sectionScores(sectionScores)
                .weaknesses(weaknesses)
                .recruiterFeedback(recruiterFeedback)
                .requestType(requestType.name())
                .build();

        return auditReportRepository.save(report);
    }

    /**
     * Executes ExperienceTranslatorAgent (request type REWRITE) for a project or coursework entry.
     * Saves the result as a PENDING suggestion in agent_suggestions.
     */
    @Transactional
    public TranslateExperienceResponse executeExperienceTranslation(
            User user,
            Profile profile,
            TranslateExperienceRequest request
    ) {
        log.info("Orchestrating REWRITE (translate-experience) for profile ID: {}",
                profile != null ? profile.getId() : null);

        Long projectId = request != null ? request.getProjectId() : null;
        StringBuilder experienceContent = new StringBuilder();
        String originalText = "[]";

        if (projectId != null) {
            Project project = projectRepository.findByIdAndProfileId(projectId, profile.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

            experienceContent.append("PROJECT TITLE: ").append(project.getTitle() != null ? project.getTitle() : "Untitled").append("\n");
            if (project.getRole() != null) experienceContent.append("ROLE: ").append(project.getRole()).append("\n");
            if (project.getDescription() != null) experienceContent.append("DESCRIPTION: ").append(project.getDescription()).append("\n");
            if (project.getTechnologies() != null) experienceContent.append("TECHNOLOGIES: ").append(project.getTechnologies()).append("\n");
            if (project.getBullets() != null && !project.getBullets().isEmpty()) {
                experienceContent.append("CURRENT BULLETS:\n");
                for (String b : project.getBullets()) {
                    experienceContent.append("- ").append(b).append("\n");
                }
                originalText = suggestionService.serializeBullets(project.getBullets());
            }
        } else if (request != null) {
            if (request.getTitle() != null) experienceContent.append("PROJECT / COURSEWORK TITLE: ").append(request.getTitle()).append("\n");
            if (request.getRole() != null) experienceContent.append("ROLE: ").append(request.getRole()).append("\n");
            if (request.getDescription() != null) experienceContent.append("DESCRIPTION: ").append(request.getDescription()).append("\n");
            if (request.getTechnologies() != null) experienceContent.append("TECHNOLOGIES: ").append(request.getTechnologies()).append("\n");
            if (request.getBullets() != null && !request.getBullets().isEmpty()) {
                experienceContent.append("CURRENT BULLETS:\n");
                for (String b : request.getBullets()) {
                    experienceContent.append("- ").append(b).append("\n");
                }
                originalText = suggestionService.serializeBullets(request.getBullets());
            }
        }

        String formattedExperience = experienceContent.toString().trim();
        if (formattedExperience.isBlank()) {
            throw new IllegalArgumentException("Cannot translate empty experience. Please provide a project ID or project details.");
        }

        String targetRole = (request != null && request.getTargetRole() != null && !request.getTargetRole().isBlank())
                ? request.getTargetRole()
                : (profile != null ? profile.getTargetRole() : "Software Engineer");

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(user)
                .profile(profile)
                .input(formattedExperience)
                .targetRole(targetRole)
                .build();

        CompletableFuture<AgentResult<ExperienceTranslationResult>> translatorFuture = CompletableFuture.supplyAsync(
                () -> experienceTranslatorAgent.execute(ctx),
                orchestratorExecutor
        ).exceptionally(ex -> {
            log.error("ExperienceTranslatorAgent threw unhandled exception: {}", ex.getMessage(), ex);
            return AgentResult.failure("Translator failed unexpectedly: " + ex.getMessage());
        });

        try {
            translatorFuture.get(ORCHESTRATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Experience translation future completed with exception or timeout: {}", e.getMessage());
        }

        AgentResult<ExperienceTranslationResult> agentResult = resolveFutureWithFallback(
                translatorFuture,
                () -> AgentResult.failure("Experience translation timed out or failed")
        );

        SuggestionResponse suggestionResponse = null;
        if (agentResult.getData() != null && agentResult.getData().getBullets() != null && !agentResult.getData().getBullets().isEmpty()) {
            String suggestedText = suggestionService.serializeBullets(agentResult.getData().getBullets());
            AgentSuggestion suggestion = suggestionService.createSuggestion(
                    profile,
                    ExperienceTranslatorAgent.AGENT_NAME,
                    "PROJECT_BULLETS",
                    projectId,
                    originalText,
                    suggestedText,
                    agentResult.getData().getRationale()
            );
            suggestionResponse = suggestionService.mapToResponse(suggestion);
        }

        return TranslateExperienceResponse.builder()
                .agentResult(agentResult)
                .suggestion(suggestionResponse)
                .build();
    }

    private void createSuggestionsFromWeaknesses(Profile profile, AgentResult<ProfileAuditResult> auditorResult) {
        if (profile == null || auditorResult == null || auditorResult.getData() == null) {
            return;
        }
        List<ProfileWeakness> weaknesses = auditorResult.getData().getWeaknesses();
        if (weaknesses == null || weaknesses.isEmpty()) {
            return;
        }

        for (ProfileWeakness weakness : weaknesses) {
            String section = weakness.getSection();
            if ("HEADLINE".equalsIgnoreCase(section) && profile.getHeadline() != null && !profile.getHeadline().isBlank()) {
                String suggested = (weakness.getImprovementSuggestion() != null && !weakness.getImprovementSuggestion().isBlank())
                        ? weakness.getImprovementSuggestion()
                        : profile.getHeadline();
                suggestionService.createSuggestion(
                        profile,
                        ProfileAuditorAgent.AGENT_NAME,
                        "HEADLINE",
                        null,
                        profile.getHeadline(),
                        suggested,
                        weakness.getReason()
                );
            } else if ("ABOUT".equalsIgnoreCase(section) && profile.getAbout() != null && !profile.getAbout().isBlank()) {
                String suggested = (weakness.getImprovementSuggestion() != null && !weakness.getImprovementSuggestion().isBlank())
                        ? weakness.getImprovementSuggestion()
                        : profile.getAbout();
                suggestionService.createSuggestion(
                        profile,
                        ProfileAuditorAgent.AGENT_NAME,
                        "ABOUT",
                        null,
                        profile.getAbout(),
                        suggested,
                        weakness.getReason()
                );
            }
        }
    }

    /**
     * Executes ProofOfSkillAgent (request type VERIFY_SKILLS).
     * Fetches public GitHub repositories via GitHubTool, evaluates evidence deterministically in Java,
     * and enriches explanations via ProofOfSkillAgent.
     */
    @Transactional(readOnly = true)
    public VerifySkillsResponse executeSkillVerification(User user, Profile profile) {
        log.info("Orchestrating VERIFY_SKILLS for profile ID: {}, github: {}",
                profile != null ? profile.getId() : null,
                profile != null ? profile.getGithubUsername() : null);

        List<ProfileSkill> claimedSkills = (profile != null && profile.getProfileSkills() != null)
                ? profile.getProfileSkills()
                : Collections.emptyList();

        String githubUsername = (profile != null) ? profile.getGithubUsername() : null;

        List<GitHubRepoDto> repos = (githubUsername != null && !githubUsername.isBlank())
                ? gitHubTool.fetchPublicRepositories(githubUsername)
                : Collections.emptyList();

        SkillVerificationResult precomputed = skillEvidenceMatcher.matchSkillsToRepositories(claimedSkills, repos);

        if (githubUsername == null || githubUsername.isBlank()) {
            precomputed.setSummary("No GitHub username configured on profile. Link your GitHub account to verify your skills against code evidence.");
            return VerifySkillsResponse.builder()
                    .githubUsername(null)
                    .totalClaimedSkills(claimedSkills.size())
                    .verifiedSkillsCount(0)
                    .agentResult(AgentResult.success(precomputed))
                    .build();
        }

        AgentContext<SkillVerificationResult> ctx = AgentContext.<SkillVerificationResult>builder()
                .user(user)
                .profile(profile)
                .input(precomputed)
                .targetRole(profile.getTargetRole())
                .build();

        CompletableFuture<AgentResult<SkillVerificationResult>> proofFuture = CompletableFuture.supplyAsync(
                () -> proofOfSkillAgent.execute(ctx),
                orchestratorExecutor
        ).exceptionally(ex -> {
            log.error("ProofOfSkillAgent threw unhandled exception: {}", ex.getMessage(), ex);
            return AgentResult.fallback(precomputed, "Proof agent error; using deterministic matcher: " + ex.getMessage());
        });

        try {
            proofFuture.get(ORCHESTRATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Skill verification future completed with exception or timeout: {}", e.getMessage());
        }

        AgentResult<SkillVerificationResult> agentResult = resolveFutureWithFallback(
                proofFuture,
                () -> AgentResult.fallback(precomputed, "Skill verification timed out; using deterministic matcher")
        );

        int verifiedCount = (agentResult.getData() != null && agentResult.getData().getSkills() != null)
                ? (int) agentResult.getData().getSkills().stream()
                .filter(s -> s.getStatus() == SkillVerificationStatus.VERIFIED)
                .count()
                : 0;

        return VerifySkillsResponse.builder()
                .githubUsername(githubUsername)
                .totalClaimedSkills(claimedSkills.size())
                .verifiedSkillsCount(verifiedCount)
                .agentResult(agentResult)
                .build();
    }
}
