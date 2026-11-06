package com.careerpilot.service.impl;

import com.careerpilot.agent.dto.CoachTaskDto;
import com.careerpilot.agent.dto.WeeklyCoachResult;
import com.careerpilot.agent.orchestrator.AgentOrchestrator;
import com.careerpilot.dto.coach.StreakResponse;
import com.careerpilot.dto.coach.WeeklyPlanResponse;
import com.careerpilot.dto.coach.WeeklyTaskResponse;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.TaskStatus;
import com.careerpilot.entity.User;
import com.careerpilot.entity.WeeklyTask;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.ProfileSkillRepository;
import com.careerpilot.repository.ProjectRepository;
import com.careerpilot.repository.WeeklyTaskRepository;
import com.careerpilot.service.WeeklyTaskService;
import com.careerpilot.tool.github.GitHubRepoDto;
import com.careerpilot.tool.github.GitHubTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyTaskServiceImpl implements WeeklyTaskService {

    private final WeeklyTaskRepository weeklyTaskRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final GitHubTool gitHubTool;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final ProfileSkillRepository profileSkillRepository;

    @Override
    @Transactional
    public WeeklyPlanResponse getOrGenerateWeeklyPlan(User user, Profile profile, boolean regenerate) {
        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        log.info("Requesting weekly plan for user ID: {}, weekStart: {}, regenerate: {}",
                user.getId(), currentWeekStart, regenerate);

        List<WeeklyTask> existing = weeklyTaskRepository.findAllByUserIdAndWeekStartOrderByCreatedAtAsc(user.getId(), currentWeekStart);

        if (!existing.isEmpty() && !regenerate) {
            log.info("Returning existing plan of {} tasks for week {}", existing.size(), currentWeekStart);
            return WeeklyPlanResponse.builder()
                    .weekStart(currentWeekStart)
                    .tasks(existing.stream().map(this::mapToResponse).toList())
                    .isNewPlan(false)
                    .focusArea("Active Weekly Milestones")
                    .message("Existing tasks loaded for current week.")
                    .build();
        }

        if (regenerate && !existing.isEmpty()) {
            log.info("Regenerating weekly plan: purging {} existing tasks for week {}", existing.size(), currentWeekStart);
            weeklyTaskRepository.deleteAll(existing);
        }

        WeeklyCoachResult coachResult = agentOrchestrator.executeWeeklyPlan(user, profile);

        List<WeeklyTask> createdTasks = new ArrayList<>();
        List<CoachTaskDto> taskDtos = (coachResult != null && coachResult.getTasks() != null)
                ? coachResult.getTasks()
                : Collections.emptyList();

        for (CoachTaskDto dto : taskDtos) {
            String estTime = (dto.getEstimatedTime() != null && !dto.getEstimatedTime().isBlank())
                    ? dto.getEstimatedTime()
                    : "~1-2 hours";
            String whyMatters = (dto.getWhyThisMatters() != null && !dto.getWhyThisMatters().isBlank())
                    ? dto.getWhyThisMatters()
                    : "Builds practical technical competence and enhances your portfolio.";
            String vType = (dto.getVerificationType() != null && !dto.getVerificationType().isBlank())
                    ? dto.getVerificationType()
                    : "SELF_REPORTED";
            String vHint = dto.getVerificationHint();

            int baselineCount = 0;
            if ("PROFILE_CHANGE".equalsIgnoreCase(vType) && profile != null && profile.getId() != null) {
                if (vHint != null && vHint.toLowerCase().contains("skill")) {
                    baselineCount = (int) profileSkillRepository.countByProfileId(profile.getId());
                } else {
                    baselineCount = (int) projectRepository.countByProfileId(profile.getId());
                }
            }

            WeeklyTask task = WeeklyTask.builder()
                    .user(user)
                    .profile(profile)
                    .weekStart(currentWeekStart)
                    .title(dto.getTitle())
                    .description(dto.getDescription())
                    .category(dto.getCategory() != null ? dto.getCategory() : "GENERAL")
                    .estimatedTime(estTime)
                    .whyThisMatters(whyMatters)
                    .verificationType(vType)
                    .verificationHint(vHint)
                    .verificationStatus("UNVERIFIED")
                    .baselineCount(baselineCount)
                    .status(TaskStatus.PENDING)
                    .dueDate(currentWeekStart.plusDays(6))
                    .build();
            createdTasks.add(weeklyTaskRepository.save(task));
        }

        return WeeklyPlanResponse.builder()
                .weekStart(currentWeekStart)
                .tasks(createdTasks.stream().map(this::mapToResponse).toList())
                .isNewPlan(true)
                .focusArea(coachResult != null ? coachResult.getFocusArea() : "Foundational Readiness")
                .message(coachResult != null ? coachResult.getRationale() : "New weekly plan generated.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyTaskResponse> getWeeklyTasks(User user, LocalDate weekStart) {
        LocalDate targetWeek = (weekStart != null)
                ? weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        return weeklyTaskRepository.findAllByUserIdAndWeekStartOrderByCreatedAtAsc(user.getId(), targetWeek).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public WeeklyTaskResponse completeTask(User user, Long taskId) {
        WeeklyTask task = weeklyTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Weekly task not found with id: " + taskId));

        // Ownership enforcement: return 404 if task belongs to another user
        if (task.getUser() == null || !task.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Weekly task not found with id: " + taskId);
        }

        if (task.getStatus() != TaskStatus.DONE) {
            task.setStatus(TaskStatus.DONE);
            task.setCompletedAt(Instant.now());

            verifyTask(task, user);

            task = weeklyTaskRepository.save(task);
            log.info("User ID: {} completed task ID: {} [verificationStatus={}]",
                    user.getId(), taskId, task.getVerificationStatus());
        }

        return mapToResponse(task);
    }

    private void verifyTask(WeeklyTask task, User user) {
        String type = task.getVerificationType();
        if (type == null || type.isBlank() || "SELF_REPORTED".equalsIgnoreCase(type)) {
            task.setVerificationStatus("SELF_REPORTED");
            return;
        }

        try {
            switch (type.toUpperCase()) {
                case "GITHUB_FILE_EXISTS" -> verifyGithubFileExists(task, user);
                case "GITHUB_RECENT_COMMIT" -> verifyGithubRecentCommit(task, user);
                case "PROFILE_CHANGE" -> verifyProfileChange(task, user);
                default -> task.setVerificationStatus("SELF_REPORTED");
            }
        } catch (Exception e) {
            log.warn("Verification failed for task ID {} ({}), gracefully falling back to SELF_REPORTED: {}",
                    task.getId(), type, e.getMessage());
            task.setVerificationStatus("SELF_REPORTED");
        }
    }

    private void verifyGithubFileExists(WeeklyTask task, User user) {
        Profile profile = resolveProfile(user);
        String githubUsername = profile.getGithubUsername();
        if (githubUsername == null || githubUsername.trim().isBlank()) {
            task.setVerificationStatus("SELF_REPORTED");
            return;
        }

        String hint = (task.getVerificationHint() != null && !task.getVerificationHint().isBlank())
                ? task.getVerificationHint().trim()
                : "README.md";

        Optional<String> repoFileProof = gitHubTool.findRepoWithFile(githubUsername.trim(), hint);
        if (repoFileProof.isPresent()) {
            task.setVerificationStatus("VERIFIED");
            task.setProofUrl(repoFileProof.get());
        } else {
            task.setVerificationStatus("SELF_REPORTED");
        }
    }

    private void verifyGithubRecentCommit(WeeklyTask task, User user) {
        Profile profile = resolveProfile(user);
        String githubUsername = profile.getGithubUsername();
        if (githubUsername == null || githubUsername.trim().isBlank()) {
            task.setVerificationStatus("SELF_REPORTED");
            return;
        }

        List<GitHubRepoDto> repos = gitHubTool.fetchPublicRepositories(githubUsername.trim());
        if (repos == null || repos.isEmpty()) {
            task.setVerificationStatus("SELF_REPORTED");
            return;
        }

        LocalDate thresholdDate = task.getWeekStart() != null ? task.getWeekStart() : LocalDate.now().minusDays(7);
        Instant thresholdInstant = thresholdDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        Optional<GitHubRepoDto> recentRepo = repos.stream()
                .filter(r -> r.getPushedAt() != null)
                .filter(r -> {
                    try {
                        Instant pushInstant = Instant.parse(r.getPushedAt());
                        return pushInstant.isAfter(thresholdInstant);
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .findFirst();

        if (recentRepo.isPresent()) {
            task.setVerificationStatus("VERIFIED");
            task.setProofUrl(recentRepo.get().getHtmlUrl());
        } else {
            task.setVerificationStatus("SELF_REPORTED");
        }
    }

    private void verifyProfileChange(WeeklyTask task, User user) {
        Profile profile = resolveProfile(user);
        int baseline = task.getBaselineCount() != null ? task.getBaselineCount() : 0;
        String hint = (task.getVerificationHint() != null) ? task.getVerificationHint().toLowerCase() : "";

        int currentCount;
        boolean hasItemAfterCreation = false;
        Instant taskCreated = task.getCreatedAt() != null ? task.getCreatedAt() : Instant.EPOCH;

        if (hint.contains("skill")) {
            currentCount = (int) profileSkillRepository.countByProfileId(profile.getId());
            hasItemAfterCreation = profileSkillRepository.existsByProfileIdAndCreatedAtAfter(profile.getId(), taskCreated);
        } else {
            currentCount = (int) projectRepository.countByProfileId(profile.getId());
            hasItemAfterCreation = projectRepository.existsByProfileIdAndCreatedAtAfter(profile.getId(), taskCreated);
        }

        if (currentCount > baseline || hasItemAfterCreation) {
            task.setVerificationStatus("VERIFIED");
            task.setProofUrl("/profile");
        } else {
            task.setVerificationStatus("SELF_REPORTED");
        }
    }

    private Profile resolveProfile(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(Profile.builder().user(user).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public StreakResponse calculateStreak(User user) {
        List<WeeklyTask> allTasks = weeklyTaskRepository.findAllByUserId(user.getId());

        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, List<WeeklyTask>> tasksByWeek = allTasks.stream()
                .filter(t -> t.getWeekStart() != null)
                .collect(Collectors.groupingBy(WeeklyTask::getWeekStart));

        List<WeeklyTask> currentWeekTasks = tasksByWeek.getOrDefault(currentWeekStart, Collections.emptyList());
        int currentWeekCompleted = (int) currentWeekTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();
        boolean currentWeekGoalMet = currentWeekCompleted >= 2;

        // 1. Calculate current active streak
        int currentStreak = 0;
        LocalDate checkWeek = currentWeekGoalMet ? currentWeekStart : currentWeekStart.minusWeeks(1);

        while (true) {
            List<WeeklyTask> weekTasks = tasksByWeek.get(checkWeek);
            if (weekTasks == null || weekTasks.isEmpty()) {
                break;
            }
            long completed = weekTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
            if (completed >= 2) {
                currentStreak++;
                checkWeek = checkWeek.minusWeeks(1);
            } else {
                break;
            }
        }

        // 2. Calculate longest historical streak
        List<LocalDate> sortedWeeks = tasksByWeek.keySet().stream().sorted().toList();
        int longestStreak = 0;
        int runningStreak = 0;
        LocalDate previousWeek = null;

        for (LocalDate w : sortedWeeks) {
            long completed = tasksByWeek.get(w).stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
            if (completed >= 2) {
                if (previousWeek != null && w.equals(previousWeek.plusWeeks(1))) {
                    runningStreak++;
                } else {
                    runningStreak = 1;
                }
                previousWeek = w;
                longestStreak = Math.max(longestStreak, runningStreak);
            } else {
                runningStreak = 0;
                previousWeek = null;
            }
        }
        longestStreak = Math.max(longestStreak, currentStreak);

        return StreakResponse.builder()
                .currentStreakWeeks(currentStreak)
                .longestStreakWeeks(longestStreak)
                .currentWeekStart(currentWeekStart)
                .currentWeekCompletedTasks(currentWeekCompleted)
                .currentWeekTotalTasks(currentWeekTasks.size())
                .currentWeekGoalMet(currentWeekGoalMet)
                .build();
    }

    private WeeklyTaskResponse mapToResponse(WeeklyTask task) {
        String estTime = (task.getEstimatedTime() != null && !task.getEstimatedTime().isBlank())
                ? task.getEstimatedTime()
                : "~1-2 hours";
        String whyMatters = (task.getWhyThisMatters() != null && !task.getWhyThisMatters().isBlank())
                ? task.getWhyThisMatters()
                : "Builds practical technical competence and enhances your portfolio.";

        return WeeklyTaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .category(task.getCategory())
                .estimatedTime(estTime)
                .whyThisMatters(whyMatters)
                .verificationType(task.getVerificationType())
                .verificationHint(task.getVerificationHint())
                .verificationStatus(task.getVerificationStatus())
                .proofUrl(task.getProofUrl())
                .status(task.getStatus())
                .weekStart(task.getWeekStart())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
