package com.careerpilot.controller;

import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.EducationRequest;
import com.careerpilot.dto.profile.ProfileRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerTest {

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
    private EducationRepository educationRepository;

    @Autowired
    private ProfileSkillRepository profileSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        profileSkillRepository.deleteAll();
        projectRepository.deleteAll();
        educationRepository.deleteAll();
        skillRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should retrieve authenticated user's profile")
    void shouldGetProfileSuccessfully() throws Exception {
        String token = registerAndGetToken("alice@college.edu", "Alice Walker");

        mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("Should update profile headline and about with sanitization")
    void shouldUpdateProfileSuccessfully() throws Exception {
        String token = registerAndGetToken("bob@college.edu", "Bob Builder");

        ProfileRequest updateRequest = ProfileRequest.builder()
                .headline(" Aspiring Cloud Engineer \n\t")
                .about("Passionate about distributed systems.")
                .githubUsername("bobbuilder")
                .targetRole("Backend Developer")
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Aspiring Cloud Engineer"))
                .andExpect(jsonPath("$.about").value("Passionate about distributed systems."))
                .andExpect(jsonPath("$.githubUsername").value("bobbuilder"));
    }

    @Test
    @DisplayName("Should reject profile update when headline is blank or exceeds 120 chars")
    void shouldRejectProfileUpdateWhenHeadlineBlankOrTooLong() throws Exception {
        String token = registerAndGetToken("charlie@college.edu", "Charlie Day");

        ProfileRequest blankHeadline = ProfileRequest.builder()
                .headline("   ")
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankHeadline)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.headline").isNotEmpty());

        String longHeadline = "A".repeat(125);
        ProfileRequest tooLongHeadline = ProfileRequest.builder()
                .headline(longHeadline)
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLongHeadline)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.headline").isNotEmpty());
    }

    @Test
    @DisplayName("Should create, retrieve, update, and delete projects with bullets")
    void shouldPerformProjectCrudSuccessfully() throws Exception {
        String token = registerAndGetToken("diana@college.edu", "Diana Prince");

        ProjectRequest projectRequest = ProjectRequest.builder()
                .title("Distributed Task Scheduler")
                .description("Built a cluster-wide task queue using Redis and Spring Boot.")
                .role("Lead Developer")
                .technologies("Java 17, Spring Boot, Redis, Docker")
                .startDate(LocalDate.of(2025, 1, 15))
                .endDate(LocalDate.of(2025, 5, 20))
                .isAcademic(true)
                .bullets(List.of("Engineered worker thread pool", "Reduced job latency by 45%"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/profile/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Distributed Task Scheduler"))
                .andExpect(jsonPath("$.bullets[0]").value("Engineered worker thread pool"))
                .andReturn();

        Long projectId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Get single project
        mockMvc.perform(get("/api/v1/profile/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Distributed Task Scheduler"));

        // Update project
        projectRequest.setTitle("Distributed Task Scheduler 2.0");
        mockMvc.perform(put("/api/v1/profile/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Distributed Task Scheduler 2.0"));

        // Delete project
        mockMvc.perform(delete("/api/v1/profile/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted (404)
        mockMvc.perform(get("/api/v1/profile/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should reject project when end date is before start date")
    void shouldRejectProjectWithInvalidDates() throws Exception {
        String token = registerAndGetToken("eve@college.edu", "Eve Polastri");

        ProjectRequest invalidDates = ProjectRequest.builder()
                .title("Time Travel Bug")
                .startDate(LocalDate.of(2025, 6, 1))
                .endDate(LocalDate.of(2025, 1, 1)) // before start date!
                .build();

        mockMvc.perform(post("/api/v1/profile/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDates)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should enforce ownership: User A cannot read, update, or delete User B's project (returns 404)")
    void shouldPreventUserAFromAccessingUserBProject() throws Exception {
        String tokenA = registerAndGetToken("usera@college.edu", "User A");
        String tokenB = registerAndGetToken("userb@college.edu", "User B");

        // User B creates a project
        ProjectRequest projectB = ProjectRequest.builder()
                .title("User B Secret Project")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/profile/projects")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectB)))
                .andExpect(status().isCreated())
                .andReturn();

        Long projectBId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // User A attempts to read User B's project -> 404 Not Found!
        mockMvc.perform(get("/api/v1/profile/projects/" + projectBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // User A attempts to update User B's project -> 404 Not Found!
        mockMvc.perform(put("/api/v1/profile/projects/" + projectBId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectB)))
                .andExpect(status().isNotFound());

        // User A attempts to delete User B's project -> 404 Not Found!
        mockMvc.perform(delete("/api/v1/profile/projects/" + projectBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should enforce ownership on Education records (returns 404 when unauthorized)")
    void shouldPreventUserAFromAccessingUserBEducation() throws Exception {
        String tokenA = registerAndGetToken("frank@college.edu", "Frank");
        String tokenB = registerAndGetToken("grace@college.edu", "Grace");

        EducationRequest eduB = EducationRequest.builder()
                .institution("MIT")
                .degree("B.S. Computer Science")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/profile/education")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduB)))
                .andExpect(status().isCreated())
                .andReturn();

        Long eduBId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // User A attempts to access User B's education -> 404 Not Found
        mockMvc.perform(get("/api/v1/profile/education/" + eduBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/profile/education/" + eduBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should add skills and reject case-insensitive duplicate skills with 409 Conflict")
    void shouldAddAndRejectDuplicateSkills() throws Exception {
        String token = registerAndGetToken("harry@college.edu", "Harry Potter");

        ProfileSkillRequest javaSkill = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("ADVANCED")
                .category("Backend")
                .build();

        // Add Java
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(javaSkill)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Java"))
                .andExpect(jsonPath("$.claimedLevel").value("ADVANCED"));

        // Attempt to add duplicate 'java' (lowercase)
        ProfileSkillRequest duplicateSkill = ProfileSkillRequest.builder()
                .name("java")
                .claimedLevel("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateSkill)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Skill already exists in your profile")));
    }

    @Test
    @DisplayName("Should support pagination for listing resources")
    void shouldSupportPaginationForProjects() throws Exception {
        String token = registerAndGetToken("irene@college.edu", "Irene Adler");

        for (int i = 1; i <= 5; i++) {
            ProjectRequest req = ProjectRequest.builder()
                    .title("Project " + i)
                    .startDate(LocalDate.of(2025, i, 1))
                    .build();

            mockMvc.perform(post("/api/v1/profile/projects")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        // Request page 0 with size 2
        mockMvc.perform(get("/api/v1/profile/projects?page=0&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("Should normalize GitHub URL and handle leading/trailing slashes and @")
    void shouldNormalizeGithubUsernameSuccessfully() throws Exception {
        String token = registerAndGetToken("github-normalizer@college.edu", "GitHub Normalizer");

        // Test with full https URL with trailing slash
        String jsonPayload = """
                {
                    "headline": "Junior Dev",
                    "githubUsername": "https://github.com/octocat/"
                }
                """;

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("octocat"));

        // Test with github.com/ without protocol
        String jsonPayload2 = """
                {
                    "headline": "Junior Dev",
                    "githubUsername": "github.com/octo-cat"
                }
                """;

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("octo-cat"));
    }

    @Test
    @DisplayName("Should reject invalid GitHub username and return specific field error message")
    void shouldRejectInvalidGithubUsername() throws Exception {
        String token = registerAndGetToken("invalid-gh@college.edu", "Invalid GH");

        // Consecutive hyphens or leading hyphen
        String jsonPayload = """
                {
                    "headline": "Junior Dev",
                    "githubUsername": "-invalid-user"
                }
                """;

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.githubUsername").value("Enter only your GitHub username, for example octocat"))
                .andExpect(jsonPath("$.message").value(containsString("githubUsername: Enter only your GitHub username, for example octocat")));
    }

    @Test
    @DisplayName("Should reject unauthenticated access to profile with 401 Unauthorized")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .fullName(fullName)
                .password("Password123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }
}
