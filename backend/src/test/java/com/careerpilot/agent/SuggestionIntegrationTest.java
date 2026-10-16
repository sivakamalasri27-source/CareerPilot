package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.agent.TranslateExperienceRequest;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.dto.profile.ProjectResponse;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.AgentSuggestion;
import com.careerpilot.entity.Project;
import com.careerpilot.entity.SuggestionStatus;
import com.careerpilot.repository.AgentSuggestionRepository;
import com.careerpilot.repository.ProjectRepository;
import com.careerpilot.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuggestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private AgentSuggestionRepository agentSuggestionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
    }

    private String registerAndGetToken(String emailPrefix) throws Exception {
        String email = emailPrefix + "." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Career Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getAccessToken();
    }

    private Long createSampleProject(String token) throws Exception {
        ProjectRequest projectRequest = ProjectRequest.builder()
                .title("Inventory Management Service")
                .description("Distributed inventory tracker built using Spring Boot and Postgres")
                .technologies("Java, Spring Boot, PostgreSQL, Docker")
                .bullets(List.of("Designed REST endpoints for stock management", "Added basic integration tests"))
                .isAcademic(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ProjectResponse.class);
        return response.getId();
    }

    @Test
    @DisplayName("Translate Experience: Valid project translates into bullets and creates PENDING suggestion")
    void testTranslateExperienceValidOutput() throws Exception {
        String token = registerAndGetToken("translator.valid");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .targetRole("Backend Engineer")
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.agentResult.data.bullets").isArray())
                .andExpect(jsonPath("$.agentResult.data.clarifyingQuestions").isArray())
                .andExpect(jsonPath("$.suggestion.status").value("PENDING"))
                .andExpect(jsonPath("$.suggestion.targetSection").value("PROJECT_BULLETS"))
                .andExpect(jsonPath("$.suggestion.targetId").value(projectId))
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        AgentSuggestion suggestion = agentSuggestionRepository.findById(suggestionId).orElseThrow();
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(suggestion.getOriginalTextHash()).isNotBlank();
        assertThat(suggestion.getSuggestedText()).contains("Spring Boot");
    }

    @Test
    @DisplayName("Approve Flow: Applies suggested bullets to target project and marks APPROVED")
    void testApproveSuggestionAppliesChange() throws Exception {
        String token = registerAndGetToken("approve.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Approve the suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // Verify database state: project bullets updated
        Project updatedProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(updatedProject.getBullets()).isNotEmpty();
        assertThat(updatedProject.getBullets().get(0)).contains("Spring Boot");
    }

    @Test
    @DisplayName("Approve Flow: Optional editedText validates and replaces suggested text")
    void testApproveWithEditedText() throws Exception {
        String token = registerAndGetToken("approve.edited");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        ApproveSuggestionRequest approveRequest = ApproveSuggestionRequest.builder()
                .editedText("[\"Custom engineered resilient API with 99.9% uptime\", \"Integrated Redis cache layer reducing query latency\"]")
                .build();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Project updatedProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(updatedProject.getBullets()).containsExactly(
                "Custom engineered resilient API with 99.9% uptime",
                "Integrated Redis cache layer reducing query latency"
        );
    }

    @Test
    @DisplayName("Reject Flow: Marks suggestion REJECTED and leaves project unchanged")
    void testRejectSuggestionChangesNothing() throws Exception {
        String token = registerAndGetToken("reject.flow");
        Long projectId = createSampleProject(token);

        List<String> originalBullets = projectRepository.findById(projectId).orElseThrow().getBullets();

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Reject the suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // Project bullets should remain exactly as originally saved
        Project projectAfterReject = projectRepository.findById(projectId).orElseThrow();
        assertThat(projectAfterReject.getBullets()).isEqualTo(originalBullets);
    }

    @Test
    @DisplayName("Stale Check: Modifying project after suggestion creation returns 409 Conflict")
    void testStaleSuggestionReturns409() throws Exception {
        String token = registerAndGetToken("stale.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Modify project directly so the target content changes (hash mismatch)
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.setBullets(List.of("Completely changed bullets by concurrent update"));
        projectRepository.save(project);

        // Attempting to approve must return 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("stale")));
    }

    @Test
    @DisplayName("Double Decision: Deciding an already APPROVED or REJECTED suggestion returns 409")
    void testDoubleDecisionReturns409() throws Exception {
        String token = registerAndGetToken("double.decision");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // First decision: approve
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Second decision attempt (approve again): 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been decided")));

        // Reject attempt on already approved suggestion: 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been decided")));
    }

    @Test
    @DisplayName("Ownership Isolation: User A cannot see or decide User B's suggestion (returns 404)")
    void testCrossUserAccessReturns404() throws Exception {
        String tokenA = registerAndGetToken("userA.flow");
        String tokenB = registerAndGetToken("userB.flow");

        Long projectIdA = createSampleProject(tokenA);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectIdA)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionIdA = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // User B tries to view User A's suggestion
        mockMvc.perform(get("/api/v1/suggestions/" + suggestionIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B tries to approve User A's suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionIdA + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B tries to reject User A's suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionIdA + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Security: Unauthenticated calls return 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/suggestions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/suggestions/1/approve"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/suggestions/1/reject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Pagination & Filtering: GET /suggestions returns newest first with status filter")
    void testPaginationAndFiltering() throws Exception {
        String token = registerAndGetToken("pagination.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest req = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        // Create 2 suggestions
        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Fetch paginated
        mockMvc.perform(get("/api/v1/suggestions?status=PENDING&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
}
