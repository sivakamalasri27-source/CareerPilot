package com.careerpilot.agent;

import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.agent.dto.SkillGapResult;
import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.agent.skillgap.SkillGapAgent;
import com.careerpilot.agent.skillgap.SkillGapCalculator;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.skillgap.SkillGapComputationResult;
import com.careerpilot.entity.*;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SkillGapUnitTest {

    @Autowired
    private SkillGapCalculator calculator;

    @Autowired
    private SkillGapAgent skillGapAgent;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @Autowired
    private com.careerpilot.repository.UserRepository userRepository;

    @Autowired
    private com.careerpilot.repository.ProfileRepository profileRepository;

    private TargetRole testRole;
    private User testUser;
    private Profile testProfile;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        testUser = userRepository.save(User.builder()
                .email("gap.tester." + java.util.UUID.randomUUID() + "@college.edu")
                .passwordHash("Password123!")
                .fullName("Gap Tester")
                .build());
        testProfile = profileRepository.save(Profile.builder()
                .user(testUser)
                .targetRole("Java Backend Developer")
                .build());

        RoleRequiredSkill reqJava = RoleRequiredSkill.builder().id(1L).skillName("java").category("Language").weight(3).priority("CORE").build();
        RoleRequiredSkill reqSpring = RoleRequiredSkill.builder().id(2L).skillName("spring boot").category("Framework").weight(3).priority("CORE").build();
        RoleRequiredSkill reqDocker = RoleRequiredSkill.builder().id(3L).skillName("docker").category("DevOps").weight(2).priority("IMPORTANT").build();

        testRole = TargetRole.builder()
                .id(1L)
                .name("Java Backend Developer")
                .description("Backend role")
                .requiredSkills(List.of(reqJava, reqSpring, reqDocker))
                .build();
    }

    @Test
    @DisplayName("SkillGapCalculator: Correctly identifies matched and missing skills with weighted percentage")
    void testCalculateGap_MatchedAndMissing() {
        ProfileSkill userJava = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("ADVANCED")
                .build();

        ProfileSkill userSpring = ProfileSkill.builder()
                .skill(Skill.builder().name("Spring Boot").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        // User has Java (3 pts) and Spring Boot (3 pts). Total role weight = 8 pts. Missing Docker (2 pts).
        SkillGapComputationResult result = calculator.calculateGap(
                testRole,
                List.of(userJava, userSpring),
                Collections.emptyMap()
        );

        assertThat(result.getRoleId()).isEqualTo(1L);
        assertThat(result.getRoleName()).isEqualTo("Java Backend Developer");
        assertThat(result.getMatchedSkills()).hasSize(2);
        assertThat(result.getMissingSkills()).hasSize(1);
        assertThat(result.getMissingSkills().get(0).getSkillName()).isEqualTo("docker");
        assertThat(result.getWeakSkills()).isEmpty();

        // 6 / 8 = 75.0%
        assertThat(result.getMatchPercentage()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("SkillGapCalculator: Identifies weak skills when claimed but unverified against GitHub")
    void testCalculateGap_WeakSkills() {
        ProfileSkill userJava = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        ProfileSkill userSpring = ProfileSkill.builder()
                .skill(Skill.builder().name("Spring Boot").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        // Java is VERIFIED, Spring Boot is UNVERIFIED on GitHub
        Map<String, SkillVerificationStatus> verificationMap = Map.of(
                "java", SkillVerificationStatus.VERIFIED,
                "springboot", SkillVerificationStatus.UNVERIFIED
        );

        SkillGapComputationResult result = calculator.calculateGap(
                testRole,
                List.of(userJava, userSpring),
                verificationMap
        );

        assertThat(result.getMatchedSkills()).hasSize(1);
        assertThat(result.getMatchedSkills().get(0).getSkillName()).isEqualTo("java");

        assertThat(result.getWeakSkills()).hasSize(1);
        assertThat(result.getWeakSkills().get(0).getSkillName()).isEqualTo("spring boot");
        assertThat(result.getWeakSkills().get(0).getReason()).contains("unverified");

        assertThat(result.getMissingSkills()).hasSize(1);
        assertThat(result.getMissingSkills().get(0).getSkillName()).isEqualTo("docker");

        // Java (3) + Spring Boot weak (3 * 0.5 = 1.5) = 4.5 / 8 = 56.3%
        assertThat(result.getMatchPercentage()).isEqualTo(56.3);
    }

    @Test
    @DisplayName("SkillGapCalculator: Throws IllegalArgumentException on null role")
    void testCalculateGap_NullRoleThrows() {
        assertThatThrownBy(() -> calculator.calculateGap(null, Collections.emptyList(), Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SkillGapAgent: Generates prioritized learning roadmap via fake LLM")
    void testSkillGapAgent_ValidOutput() {
        ProfileSkill userJava = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        SkillGapComputationResult computation = calculator.calculateGap(
                testRole,
                List.of(userJava),
                Collections.emptyMap()
        );

        AgentContext<SkillGapComputationResult> ctx = AgentContext.<SkillGapComputationResult>builder()
                .user(testUser)
                .profile(testProfile)
                .input(computation)
                .targetRole(testRole.getName())
                .build();

        AgentResult<SkillGapResult> result = skillGapAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();

        SkillGapResult data = result.getData();
        assertThat(data.getLearningRoadmap()).isNotEmpty();
        assertThat(data.getLearningRoadmap().get(0).getSkillName()).isEqualTo("docker");
        assertThat(data.getLearningRoadmap().get(0).getWhyItMatters()).isNotBlank();
        assertThat(data.getLearningRoadmap().get(0).getWhatToLearn()).isNotBlank();
        assertThat(data.getLearningRoadmap().get(0).getSmallProjectIdea()).isNotBlank();
    }

    @Test
    @DisplayName("SkillGapAgent: Falls back to deterministic curriculum defaults on malformed JSON")
    void testSkillGapAgent_FallbackOnMalformedJson() {
        fakeLlmClient.setDefaultResponse("{ broken json string without closing brackets");

        ProfileSkill userJava = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        SkillGapComputationResult computation = calculator.calculateGap(
                testRole,
                List.of(userJava),
                Collections.emptyMap()
        );

        AgentContext<SkillGapComputationResult> ctx = AgentContext.<SkillGapComputationResult>builder()
                .user(testUser)
                .profile(testProfile)
                .input(computation)
                .targetRole(testRole.getName())
                .build();

        AgentResult<SkillGapResult> result = skillGapAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FALLBACK);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getLearningRoadmap()).isNotEmpty();
        assertThat(result.getErrorMessage()).containsIgnoringCase("fallback");
    }
}
