package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.dto.skillgap.SkillGapRequest;
import com.careerpilot.dto.skillgap.SkillGapResponse;
import com.careerpilot.entity.SkillGapReport;
import com.careerpilot.repository.SkillGapReportRepository;
import com.careerpilot.repository.TargetRoleRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillGapIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private TargetRoleRepository targetRoleRepository;

    @Autowired
    private SkillGapReportRepository skillGapReportRepository;

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
                .fullName("Skill Gap Candidate")
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
    @DisplayName("GET /api/v1/roles: Returns curated target roles list")
    void testGetTargetRoles_Success() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].requiredSkills").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/agents/skill-gap: Returns 401 when unauthorized")
    void testSkillGap_Unauthorized() throws Exception {
        SkillGapRequest request = SkillGapRequest.builder().roleId(1L).build();

        mockMvc.perform(post("/api/v1/agents/skill-gap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/agents/skill-gap: Returns 404 when role does not exist")
    void testSkillGap_InvalidRole_Returns404() throws Exception {
        String token = registerAndGetToken("invalidrole");
        SkillGapRequest request = SkillGapRequest.builder().roleId(999999L).build();

        mockMvc.perform(post("/api/v1/agents/skill-gap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Target role not found")));
    }

    @Test
    @DisplayName("POST /api/v1/agents/skill-gap: Successfully analyzes gap and generates roadmap")
    void testSkillGap_Success() throws Exception {
        String token = registerAndGetToken("gapsuccess");

        // 1. Add Java skill to profile
        ProfileSkillRequest skillRequest = ProfileSkillRequest.builder()
                .name("java")
                .claimedLevel("ADVANCED")
                .build();

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillRequest)))
                .andExpect(status().isCreated());

        // 2. Query target roles to get first role ID
        Long roleId = targetRoleRepository.findAll().get(0).getId();

        SkillGapRequest request = SkillGapRequest.builder().roleId(roleId).build();

        MvcResult result = mockMvc.perform(post("/api/v1/agents/skill-gap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andExpect(jsonPath("$.matchPercentage").isNumber())
                .andExpect(jsonPath("$.matchedSkills").isArray())
                .andExpect(jsonPath("$.missingSkills").isArray())
                .andExpect(jsonPath("$.learningRoadmap").isArray())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andReturn();

        SkillGapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SkillGapResponse.class);

        // Verify database persistence in skill_gap_reports
        assertThat(response.getId()).isNotNull();
        SkillGapReport persistedReport = skillGapReportRepository.findById(response.getId()).orElse(null);
        assertThat(persistedReport).isNotNull();
        assertThat(persistedReport.getMatchPercentage()).isEqualTo(response.getMatchPercentage());
        assertThat(persistedReport.getMatchedSkills()).isNotEmpty();
    }
}
