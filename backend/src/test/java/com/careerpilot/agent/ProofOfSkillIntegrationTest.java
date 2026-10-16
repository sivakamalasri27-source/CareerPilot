package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.ProfileRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.repository.*;
import com.careerpilot.tool.github.GitHubRepoDto;
import com.careerpilot.tool.github.GitHubTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProofOfSkillIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @MockBean
    private GitHubTool gitHubTool;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ProfileSkillRepository profileSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
                .fullName("Skill Candidate")
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
    @DisplayName("Should return 401 when unauthenticated")
    void testVerifySkills_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/agents/verify-skills"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return empty verification when profile has no GitHub username")
    void testVerifySkills_NoGitHubUsername() throws Exception {
        String token = registerAndGetToken("nogithub");

        mockMvc.perform(post("/api/v1/agents/verify-skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").doesNotExist())
                .andExpect(jsonPath("$.verifiedSkillsCount").value(0))
                .andExpect(jsonPath("$.agentResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.agentResult.data.summary").value(org.hamcrest.Matchers.containsString("No GitHub username configured")));
    }

    @Test
    @DisplayName("Should verify skills successfully against public GitHub repositories")
    void testVerifySkills_Success() throws Exception {
        String token = registerAndGetToken("octodev");

        // 1. Update profile with github username
        ProfileRequest profileRequest = ProfileRequest.builder()
                .headline("Full Stack Engineer | Java & Cloud")
                .about("Passionate learner with projects in microservices and cloud.")
                .githubUsername("octodev")
                .targetRole("Backend Engineer")
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileRequest)))
                .andExpect(status().isOk());

        // 2. Add claimed skill: Java
        ProfileSkillRequest skillRequest = ProfileSkillRequest.builder()
                .name("java")
                .claimedLevel("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillRequest)))
                .andExpect(status().isCreated());

        // 3. Mock GitHubTool to return public repositories for octodev
        GitHubRepoDto repo = GitHubRepoDto.builder()
                .name("ecommerce-api")
                .htmlUrl("https://github.com/octodev/ecommerce-api")
                .language("Java")
                .fork(false)
                .build();

        when(gitHubTool.fetchPublicRepositories(eq("octodev"))).thenReturn(List.of(repo));

        // 4. Trigger skill verification
        mockMvc.perform(post("/api/v1/agents/verify-skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("octodev"))
                .andExpect(jsonPath("$.totalClaimedSkills").value(1))
                .andExpect(jsonPath("$.verifiedSkillsCount").value(1))
                .andExpect(jsonPath("$.agentResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.agentResult.data.skills[0].skillName").value("java"))
                .andExpect(jsonPath("$.agentResult.data.skills[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.agentResult.data.skills[0].evidence[0].repoName").value("ecommerce-api"));
    }
}
