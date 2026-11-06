package com.careerpilot.agent;

import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.entity.*;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.ProjectRepository;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.WeeklyTaskRepository;
import com.careerpilot.tool.github.GitHubRepoDto;
import com.careerpilot.tool.github.GitHubTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeeklyCoachVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WeeklyTaskRepository weeklyTaskRepository;

    @MockBean
    private GitHubTool gitHubTool;

    @BeforeEach
    void setUp() {
        // Reset or default mocks if necessary
    }

    private record UserWithAuth(User user, Profile profile, String token) {}

    private UserWithAuth registerUserWithProfile(String prefix, String githubUsername) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Verification Candidate")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        User user = userRepository.findByEmail(email).orElseThrow();
        Profile profile = profileRepository.findByUserId(user.getId()).orElseGet(() ->
                profileRepository.save(Profile.builder().user(user).build()));

        if (githubUsername != null) {
            profile.setGithubUsername(githubUsername);
            profile = profileRepository.save(profile);
        }

        return new UserWithAuth(user, profile, authResponse.getAccessToken());
    }

    @Test
    @DisplayName("CompleteTask: GITHUB_FILE_EXISTS verifies and captures proofUrl when file is found")
    void testCompleteTask_Verified_GithubFileExists() throws Exception {
        UserWithAuth userAuth = registerUserWithProfile("ghfile", "octocat");

        when(gitHubTool.findRepoWithFile(eq("octocat"), eq("Dockerfile")))
                .thenReturn(Optional.of("https://github.com/octocat/backend-service/blob/main/Dockerfile"));

        WeeklyTask task = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userAuth.user())
                .profile(userAuth.profile())
                .weekStart(LocalDate.now())
                .title("Containerize Service")
                .description("Add multi-stage Dockerfile")
                .category("PROJECT_IMPROVEMENT")
                .verificationType("GITHUB_FILE_EXISTS")
                .verificationHint("Dockerfile")
                .verificationStatus("UNVERIFIED")
                .status(TaskStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.proofUrl").value("https://github.com/octocat/backend-service/blob/main/Dockerfile"));
    }

    @Test
    @DisplayName("CompleteTask: GITHUB_RECENT_COMMIT verifies when recent commit/push is detected")
    void testCompleteTask_Verified_GithubRecentCommit() throws Exception {
        UserWithAuth userAuth = registerUserWithProfile("ghcommit", "octocat");

        when(gitHubTool.fetchPublicRepositories(eq("octocat")))
                .thenReturn(List.of(GitHubRepoDto.builder()
                        .name("octo-repo")
                        .htmlUrl("https://github.com/octocat/octo-repo")
                        .pushedAt(Instant.now().toString())
                        .build()));

        WeeklyTask task = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userAuth.user())
                .profile(userAuth.profile())
                .weekStart(LocalDate.now())
                .title("Write Unit Tests")
                .description("Push test suite")
                .category("SKILL_BUILDING")
                .verificationType("GITHUB_RECENT_COMMIT")
                .verificationHint(null)
                .verificationStatus("UNVERIFIED")
                .status(TaskStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.proofUrl").value("https://github.com/octocat/octo-repo"));
    }

    @Test
    @DisplayName("CompleteTask: PROFILE_CHANGE verifies when new profile item (project/skill) is added")
    void testCompleteTask_Verified_ProfileChange() throws Exception {
        UserWithAuth userAuth = registerUserWithProfile("profchange", null);

        WeeklyTask task = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userAuth.user())
                .profile(userAuth.profile())
                .weekStart(LocalDate.now())
                .title("Add Portfolio Project")
                .description("Add fullstack project to profile")
                .category("PROFILE_OPTIMIZATION")
                .verificationType("PROFILE_CHANGE")
                .verificationHint("projects")
                .baselineCount(0)
                .verificationStatus("UNVERIFIED")
                .status(TaskStatus.PENDING)
                .build());

        // User adds a project after task creation
        projectRepository.save(Project.builder()
                .profile(userAuth.profile())
                .title("E-Commerce Microservices")
                .description("Production Spring Boot app")
                .technologies("Java, Spring Boot, PostgreSQL")
                .role("Backend Engineer")
                .bullets(List.of("Designed REST endpoints with JWT authentication"))
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.proofUrl").value("/profile"));
    }

    @Test
    @DisplayName("CompleteTask: When evidence is NOT found, still marks DONE with SELF_REPORTED")
    void testCompleteTask_NotFound_FallsBackToSelfReported() throws Exception {
        UserWithAuth userAuth = registerUserWithProfile("notfound", "emptyuser");

        when(gitHubTool.findRepoWithFile(eq("emptyuser"), anyString()))
                .thenReturn(Optional.empty());

        WeeklyTask task = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userAuth.user())
                .profile(userAuth.profile())
                .weekStart(LocalDate.now())
                .title("Add Kubernetes Manifest")
                .description("Create deployment.yaml")
                .category("PROJECT_IMPROVEMENT")
                .verificationType("GITHUB_FILE_EXISTS")
                .verificationHint("deployment.yaml")
                .verificationStatus("UNVERIFIED")
                .status(TaskStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verificationStatus").value("SELF_REPORTED"));
    }

    @Test
    @DisplayName("CompleteTask: Never blocks user if GitHub is down or throws error; falls back to SELF_REPORTED")
    void testCompleteTask_GitHubDown_GracefulFallbackToSelfReported() throws Exception {
        UserWithAuth userAuth = registerUserWithProfile("ghdown", "downuser");

        when(gitHubTool.findRepoWithFile(eq("downuser"), anyString()))
                .thenThrow(new RuntimeException("GitHub 503 Service Unavailable"));

        WeeklyTask task = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userAuth.user())
                .profile(userAuth.profile())
                .weekStart(LocalDate.now())
                .title("Add README Diagram")
                .description("Create architecture diagram")
                .category("PROFILE_OPTIMIZATION")
                .verificationType("GITHUB_FILE_EXISTS")
                .verificationHint("README.md")
                .verificationStatus("UNVERIFIED")
                .status(TaskStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verificationStatus").value("SELF_REPORTED"));
    }

    @Test
    @DisplayName("CompleteTask: Ownership check returns 404 if User B attempts to complete User A's task")
    void testCompleteTask_OwnershipEnforcement_Returns404() throws Exception {
        UserWithAuth userA = registerUserWithProfile("usera", "user-a");
        UserWithAuth userB = registerUserWithProfile("userb", "user-b");

        WeeklyTask taskA = weeklyTaskRepository.save(WeeklyTask.builder()
                .user(userA.user())
                .profile(userA.profile())
                .weekStart(LocalDate.now())
                .title("User A's Task")
                .description("Private task")
                .category("SKILL_BUILDING")
                .verificationType("SELF_REPORTED")
                .status(TaskStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + taskA.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userB.token()))
                .andExpect(status().isNotFound());
    }
}
