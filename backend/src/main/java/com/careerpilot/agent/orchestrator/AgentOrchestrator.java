package com.careerpilot.agent.orchestrator;

import com.careerpilot.agent.auditor.ProfileAuditorAgent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.*;
import com.careerpilot.agent.recruiter.RecruiterSimulatorAgent;
import com.careerpilot.agent.util.ProfileFormatter;
import com.careerpilot.entity.AuditReport;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.repository.AuditReportRepository;
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
    private final AuditReportRepository auditReportRepository;

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
}
