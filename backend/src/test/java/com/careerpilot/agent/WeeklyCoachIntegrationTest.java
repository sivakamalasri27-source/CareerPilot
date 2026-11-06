package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.coach.StreakResponse;
import com.careerpilot.dto.coach.WeeklyPlanResponse;
import com.careerpilot.dto.coach.WeeklyTaskResponse;
import com.careerpilot.entity.TaskStatus;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.WeeklyTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeeklyCoachIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private WeeklyTaskRepository weeklyTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
    }

    private String registerAndGetToken(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Coach Candidate")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getAccessToken();
    }

    @Test
    @DisplayName("POST /api/v1/coach/weekly-tasks/generate: Returns 401 when unauthorized")
    void testGeneratePlan_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/coach/weekly-tasks/generate: Creates exactly 3 tasks and is idempotent within the week")
    void testGeneratePlan_ExactThreeTasks_AndIdempotency() throws Exception {
        String token = registerAndGetToken("idempotent");

        // 1. Initial generation -> generates 3 new tasks
        MvcResult firstResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.newPlan").value(true))
                .andExpect(jsonPath("$.tasks[0].status").value("PENDING"))
                .andExpect(jsonPath("$.tasks[0].estimatedTime").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0].whyThisMatters").isNotEmpty())
                .andReturn();

        WeeklyPlanResponse firstPlan = objectMapper.readValue(firstResult.getResponse().getContentAsString(), WeeklyPlanResponse.class);
        List<Long> firstTaskIds = firstPlan.getTasks().stream().map(WeeklyTaskResponse::getId).toList();

        // 2. Second generation in same week without regenerate flag -> returns same tasks (idempotent)
        MvcResult secondResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.newPlan").value(false))
                .andReturn();

        WeeklyPlanResponse secondPlan = objectMapper.readValue(secondResult.getResponse().getContentAsString(), WeeklyPlanResponse.class);
        List<Long> secondTaskIds = secondPlan.getTasks().stream().map(WeeklyTaskResponse::getId).toList();

        assertThat(secondTaskIds).containsExactlyElementsOf(firstTaskIds);

        // 3. Third generation with regenerate=true -> generates 3 new tasks
        MvcResult regenResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate?regenerate=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.newPlan").value(true))
                .andReturn();

        WeeklyPlanResponse regenPlan = objectMapper.readValue(regenResult.getResponse().getContentAsString(), WeeklyPlanResponse.class);
        List<Long> regenTaskIds = regenPlan.getTasks().stream().map(WeeklyTaskResponse::getId).toList();

        // The newly generated task IDs must be different from the first batch
        assertThat(regenTaskIds).doesNotContainAnyElementsOf(firstTaskIds);
    }

    @Test
    @DisplayName("GET /api/v1/coach/weekly-tasks: Retrieves current weekly tasks for authenticated user")
    void testGetWeeklyTasks_Success() throws Exception {
        String token = registerAndGetToken("gettasks");

        // Generate tasks
        mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Retrieve tasks
        mockMvc.perform(get("/api/v1/coach/weekly-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("PATCH /api/v1/coach/weekly-tasks/{id}/complete: Completes task and enforces ownership (404 for others)")
    void testCompleteTask_OwnerAndOwnershipEnforcement() throws Exception {
        String tokenUserA = registerAndGetToken("usera");
        String tokenUserB = registerAndGetToken("userb");

        // User A generates tasks
        MvcResult planResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andReturn();

        WeeklyPlanResponse plan = objectMapper.readValue(planResult.getResponse().getContentAsString(), WeeklyPlanResponse.class);
        Long taskAId = plan.getTasks().get(0).getId();

        // User B attempts to complete User A's task -> 404 Not Found (ownership check)
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + taskAId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        // User A completes their own task -> 200 OK
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + taskAId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskAId))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // Attempting to complete non-existent task -> 404
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/999999/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/coach/streak: Tracks task completions and updates streak status")
    void testGetStreak_LiveTracking() throws Exception {
        String token = registerAndGetToken("streaktracker");

        // Generate plan
        MvcResult planResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        WeeklyPlanResponse plan = objectMapper.readValue(planResult.getResponse().getContentAsString(), WeeklyPlanResponse.class);
        Long task1Id = plan.getTasks().get(0).getId();
        Long task2Id = plan.getTasks().get(1).getId();

        // Complete 1 task: current completed = 1, goal not met yet
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task1Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coach/streak")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekCompletedTasks").value(1))
                .andExpect(jsonPath("$.currentWeekGoalMet").value(false));

        // Complete 2nd task: current completed = 2, goal met!
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task2Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coach/streak")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekCompletedTasks").value(2))
                .andExpect(jsonPath("$.currentWeekGoalMet").value(true))
                .andExpect(jsonPath("$.currentStreakWeeks").value(1));
    }
}
