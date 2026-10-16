package com.careerpilot.agent;

import com.careerpilot.agent.coach.WeeklyCoachAgent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.agent.dto.WeeklyCoachResult;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.coach.StreakResponse;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.TaskStatus;
import com.careerpilot.entity.User;
import com.careerpilot.entity.WeeklyTask;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.WeeklyTaskRepository;
import com.careerpilot.service.WeeklyTaskService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WeeklyCoachUnitTest {

    @Autowired
    private WeeklyCoachAgent weeklyCoachAgent;

    @Autowired
    private WeeklyTaskService weeklyTaskService;

    @Autowired
    private WeeklyTaskRepository weeklyTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @Autowired
    private com.careerpilot.repository.ProfileRepository profileRepository;

    private User sampleUser;
    private Profile sampleProfile;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        sampleUser = userRepository.save(User.builder()
                .email("streak.test." + UUID.randomUUID() + "@college.edu")
                .passwordHash("Password123!")
                .fullName("Streak Tester")
                .build());

        sampleProfile = profileRepository.save(Profile.builder()
                .user(sampleUser)
                .headline("Aspiring Backend Developer")
                .build());
    }

    @Test
    @DisplayName("WeeklyCoachAgent: Generates exactly 3 concrete tasks")
    void testWeeklyCoachAgent_ExactThreeTasks() {
        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input("Audit showed weak README and missing Docker skills.")
                .targetRole("Backend Developer")
                .build();

        AgentResult<WeeklyCoachResult> result = weeklyCoachAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getTasks()).hasSize(3);
        assertThat(result.getData().getTasks().get(0).getTitle()).isNotBlank();
        assertThat(result.getData().getTasks().get(0).getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("WeeklyCoachAgent: Falls back to 3 foundational tasks on empty context or malformed JSON")
    void testWeeklyCoachAgent_FallbackProducesThreeTasks() {
        fakeLlmClient.setDefaultResponse("{ totally invalid json string");

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input("")
                .targetRole("Backend Developer")
                .build();

        AgentResult<WeeklyCoachResult> result = weeklyCoachAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FALLBACK);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getTasks()).hasSize(3);
        assertThat(result.getData().getFocusArea()).contains("Foundational");
    }

    @Test
    @DisplayName("StreakCalculation: Accurately counts consecutive weeks where >= 2 tasks completed")
    void testStreakCalculation_ConsecutiveWeeks() {
        LocalDate currentMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate twoWeeksAgoMonday = currentMonday.minusWeeks(2);
        LocalDate lastWeekMonday = currentMonday.minusWeeks(1);

        // Week -2: 3 tasks, 2 DONE
        createTask(twoWeeksAgoMonday, TaskStatus.DONE);
        createTask(twoWeeksAgoMonday, TaskStatus.DONE);
        createTask(twoWeeksAgoMonday, TaskStatus.PENDING);

        // Week -1: 3 tasks, 3 DONE
        createTask(lastWeekMonday, TaskStatus.DONE);
        createTask(lastWeekMonday, TaskStatus.DONE);
        createTask(lastWeekMonday, TaskStatus.DONE);

        // Current Week (in progress): 1 task DONE so far (goal not met yet, but streak maintained from last week)
        createTask(currentMonday, TaskStatus.DONE);
        createTask(currentMonday, TaskStatus.PENDING);
        createTask(currentMonday, TaskStatus.PENDING);

        StreakResponse streak = weeklyTaskService.calculateStreak(sampleUser);

        // Streak from prior 2 weeks should be 2!
        assertThat(streak.getCurrentStreakWeeks()).isEqualTo(2);
        assertThat(streak.getCurrentWeekCompletedTasks()).isEqualTo(1);
        assertThat(streak.isCurrentWeekGoalMet()).isFalse();

        // Now user completes a 2nd task in current week:
        WeeklyTask currentWeekTask2 = createTask(currentMonday, TaskStatus.DONE);
        StreakResponse updatedStreak = weeklyTaskService.calculateStreak(sampleUser);

        // Streak should now include the current week = 3!
        assertThat(updatedStreak.getCurrentStreakWeeks()).isEqualTo(3);
        assertThat(updatedStreak.isCurrentWeekGoalMet()).isTrue();
    }

    @Test
    @DisplayName("StreakCalculation: Breaks streak if a previous week completed < 2 tasks")
    void testStreakCalculation_StreakBroken() {
        LocalDate currentMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate twoWeeksAgoMonday = currentMonday.minusWeeks(2);
        LocalDate lastWeekMonday = currentMonday.minusWeeks(1);

        // Week -2: 2 DONE
        createTask(twoWeeksAgoMonday, TaskStatus.DONE);
        createTask(twoWeeksAgoMonday, TaskStatus.DONE);

        // Week -1: ONLY 1 DONE (Broke the streak!)
        createTask(lastWeekMonday, TaskStatus.DONE);
        createTask(lastWeekMonday, TaskStatus.PENDING);

        // Current Week: 0 DONE
        createTask(currentMonday, TaskStatus.PENDING);

        StreakResponse streak = weeklyTaskService.calculateStreak(sampleUser);

        // Current week goal not met, and last week had < 2 done -> streak = 0!
        assertThat(streak.getCurrentStreakWeeks()).isEqualTo(0);
        assertThat(streak.getLongestStreakWeeks()).isEqualTo(1); // from week -2
    }

    private WeeklyTask createTask(LocalDate weekStart, TaskStatus status) {
        return weeklyTaskRepository.save(WeeklyTask.builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .weekStart(weekStart)
                .title("Sample Test Task")
                .description("Sample Description")
                .category("TESTING")
                .status(status)
                .build());
    }
}
