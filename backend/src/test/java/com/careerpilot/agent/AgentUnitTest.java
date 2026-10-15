package com.careerpilot.agent;

import com.careerpilot.agent.auditor.ProfileAuditorAgent;
import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.agent.dto.ProfileAuditResult;
import com.careerpilot.agent.dto.RecruiterDecision;
import com.careerpilot.agent.dto.RecruiterSimulationResult;
import com.careerpilot.agent.recruiter.RecruiterSimulatorAgent;
import com.careerpilot.agent.util.ProfileFormatter;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.entity.*;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentUnitTest {

    @Autowired
    private ProfileAuditorAgent profileAuditorAgent;

    @Autowired
    private RecruiterSimulatorAgent recruiterSimulatorAgent;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @Autowired
    private com.careerpilot.repository.UserRepository userRepository;

    private Profile sampleProfile;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        sampleUser = userRepository.save(User.builder()
                .email("agent.tester." + java.util.UUID.randomUUID() + "@college.edu")
                .passwordHash("Password123!")
                .fullName("Alex Chen")
                .build());

        Skill javaSkill = Skill.builder().id(1L).name("java").category("Backend").build();

        ProfileSkill profileSkill = ProfileSkill.builder()
                .id(1L)
                .skill(javaSkill)
                .claimedLevel("INTERMEDIATE")
                .build();

        Project project = Project.builder()
                .id(1L)
                .title("E-Commerce Microservices")
                .description("Backend REST API built with Spring Boot and PostgreSQL")
                .technologies("Java, Spring Boot, Docker, PostgreSQL")
                .repoUrl("https://github.com/alex/ecommerce")
                .bullets(List.of("Designed 5 microservices", "Achieved 85% test coverage"))
                .build();

        Education education = Education.builder()
                .id(1L)
                .institution("Tech University")
                .degree("Bachelor of Science")
                .fieldOfStudy("Computer Science")
                .gradeOrGpa("3.85")
                .build();

        sampleProfile = Profile.builder()
                .id(101L)
                .user(sampleUser)
                .headline("Aspiring Backend Engineer | Java & Spring Boot")
                .targetRole("Backend Engineer")
                .about("Passionate computer science student building reliable backend systems.")
                .profileSkills(List.of(profileSkill))
                .projects(List.of(project))
                .education(List.of(education))
                .build();
    }

    @Test
    @DisplayName("ProfileAuditorAgent: Generates valid score, section scores, and weaknesses")
    void testProfileAuditorAgentValidOutput() {
        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(ProfileFormatter.formatProfileForAi(sampleProfile))
                .targetRole("Backend Engineer")
                .build();

        AgentResult<ProfileAuditResult> result = profileAuditorAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();

        ProfileAuditResult audit = result.getData();
        assertThat(audit.getOverallScore()).isEqualTo(85);
        assertThat(audit.getSectionScores().getHeadline()).isEqualTo(80);
        assertThat(audit.getSectionScores().getProjects()).isEqualTo(85);
        assertThat(audit.getWeaknesses()).isNotEmpty();
        assertThat(audit.getWeaknesses().get(0).getWeakText()).contains("Built REST API");
    }

    @Test
    @DisplayName("ProfileAuditorAgent: Falls back gracefully on malformed LLM JSON")
    void testProfileAuditorAgentFallbackOnMalformedJson() {
        fakeLlmClient.setDefaultResponse("{ totally broken json response without closing braces");

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(ProfileFormatter.formatProfileForAi(sampleProfile))
                .targetRole("Backend Engineer")
                .build();

        AgentResult<ProfileAuditResult> result = profileAuditorAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FALLBACK);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getOverallScore()).isEqualTo(50);
        assertThat(result.getErrorMessage()).contains("fallback");
    }

    @Test
    @DisplayName("RecruiterSimulatorAgent: Generates three personas and consensus decision")
    void testRecruiterSimulatorAgentValidOutput() {
        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(ProfileFormatter.formatProfileForAi(sampleProfile))
                .targetRole("Backend Engineer")
                .build();

        AgentResult<RecruiterSimulationResult> result = recruiterSimulatorAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();

        RecruiterSimulationResult sim = result.getData();
        assertThat(sim.getPersonas()).hasSize(3);
        assertThat(sim.getConsensusDecision()).isEqualTo(RecruiterDecision.YES);
        assertThat(sim.getPersonas().get(0).getPersonaTitle()).contains("MNC HR");
        assertThat(sim.getPersonas().get(1).getPersonaTitle()).contains("Startup Founder");
        assertThat(sim.getPersonas().get(2).getPersonaTitle()).contains("Technical Lead");
    }

    @Test
    @DisplayName("RecruiterSimulatorAgent: Falls back gracefully on malformed LLM JSON")
    void testRecruiterSimulatorAgentFallbackOnMalformedJson() {
        fakeLlmClient.setDefaultResponse("{ invalid recruiter response !!!");

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(ProfileFormatter.formatProfileForAi(sampleProfile))
                .targetRole("Backend Engineer")
                .build();

        AgentResult<RecruiterSimulationResult> result = recruiterSimulatorAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FALLBACK);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getConsensusDecision()).isEqualTo(RecruiterDecision.MAYBE);
        assertThat(result.getData().getPersonas()).hasSize(3);
    }
}
