package com.careerpilot.agent;

import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.agent.dto.SkillVerificationResult;
import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.agent.skill.ProofOfSkillAgent;
import com.careerpilot.agent.skill.SkillEvidenceMatcher;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.Skill;
import com.careerpilot.entity.User;
import com.careerpilot.tool.github.GitHubRepoDto;
import com.careerpilot.tool.github.GitHubTool;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ProofOfSkillUnitTest {

    @Autowired
    private SkillEvidenceMatcher matcher;

    @Autowired
    private ProofOfSkillAgent proofOfSkillAgent;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    private User sampleUser;
    private Profile sampleProfile;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        sampleUser = User.builder()
                .id(1L)
                .email("skills.tester@college.edu")
                .fullName("Skills Tester")
                .build();

        sampleProfile = Profile.builder()
                .id(10L)
                .user(sampleUser)
                .githubUsername("devtester")
                .targetRole("Backend Engineer")
                .build();
    }

    @Test
    @DisplayName("SkillEvidenceMatcher: Classifies as VERIFIED when primary language matches")
    void testMatchSkills_VerifiedPrimaryLanguage() {
        ProfileSkill javaClaim = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        GitHubRepoDto repo = GitHubRepoDto.builder()
                .name("ecommerce-backend")
                .htmlUrl("https://github.com/devtester/ecommerce-backend")
                .language("Java")
                .fork(false)
                .build();

        SkillVerificationResult result = matcher.matchSkillsToRepositories(List.of(javaClaim), List.of(repo));

        assertThat(result.getOverallVerifiedPercentage()).isEqualTo(100.0);
        assertThat(result.getSkills()).hasSize(1);
        assertThat(result.getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.VERIFIED);
        assertThat(result.getSkills().get(0).getEvidence()).isNotEmpty();
        assertThat(result.getSkills().get(0).getEvidence().get(0).getReason()).contains("Primary language");
    }

    @Test
    @DisplayName("SkillEvidenceMatcher: Classifies as VERIFIED when multiple repos match via topics")
    void testMatchSkills_VerifiedMultipleRepos() {
        ProfileSkill dockerClaim = ProfileSkill.builder()
                .skill(Skill.builder().name("Docker").build())
                .claimedLevel("BEGINNER")
                .build();

        GitHubRepoDto repo1 = GitHubRepoDto.builder()
                .name("service-one")
                .topics(List.of("docker", "microservices"))
                .language("Go")
                .build();

        GitHubRepoDto repo2 = GitHubRepoDto.builder()
                .name("service-two")
                .topics(List.of("docker-compose", "k8s"))
                .language("Python")
                .build();

        SkillVerificationResult result = matcher.matchSkillsToRepositories(List.of(dockerClaim), List.of(repo1, repo2));

        assertThat(result.getOverallVerifiedPercentage()).isEqualTo(100.0);
        assertThat(result.getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.VERIFIED);
        assertThat(result.getSkills().get(0).getEvidence()).hasSize(2);
    }

    @Test
    @DisplayName("SkillEvidenceMatcher: Classifies as PARTIAL on single non-primary match")
    void testMatchSkills_PartialSingleNonPrimaryMatch() {
        ProfileSkill redisClaim = ProfileSkill.builder()
                .skill(Skill.builder().name("Redis").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        GitHubRepoDto repo = GitHubRepoDto.builder()
                .name("fast-cache")
                .description("In-memory distributed caching with Redis and Spring")
                .language("Java")
                .build();

        SkillVerificationResult result = matcher.matchSkillsToRepositories(List.of(redisClaim), List.of(repo));

        assertThat(result.getOverallVerifiedPercentage()).isEqualTo(0.0); // 0 verified, 1 partial
        assertThat(result.getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.PARTIAL);
        assertThat(result.getSkills().get(0).getEvidence()).hasSize(1);
        assertThat(result.getSkills().get(0).getEvidence().get(0).getReason()).contains("repository name or description");
    }

    @Test
    @DisplayName("SkillEvidenceMatcher: Classifies as UNVERIFIED when no matching repos found")
    void testMatchSkills_UnverifiedWhenNoMatches() {
        ProfileSkill rustClaim = ProfileSkill.builder()
                .skill(Skill.builder().name("Rust").build())
                .claimedLevel("ADVANCED")
                .build();

        GitHubRepoDto repo = GitHubRepoDto.builder()
                .name("java-starter")
                .language("Java")
                .description("Simple Spring web app")
                .build();

        SkillVerificationResult result = matcher.matchSkillsToRepositories(List.of(rustClaim), List.of(repo));

        assertThat(result.getOverallVerifiedPercentage()).isEqualTo(0.0);
        assertThat(result.getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.UNVERIFIED);
        assertThat(result.getSkills().get(0).getEvidence()).isEmpty();
    }

    @Test
    @DisplayName("SkillEvidenceMatcher: Handles empty or null inputs gracefully")
    void testMatchSkills_EmptyAndNullInputs() {
        SkillVerificationResult resultEmpty = matcher.matchSkillsToRepositories(Collections.emptyList(), Collections.emptyList());
        assertThat(resultEmpty.getOverallVerifiedPercentage()).isEqualTo(0.0);
        assertThat(resultEmpty.getSkills()).isEmpty();

        ProfileSkill claim = ProfileSkill.builder().skill(Skill.builder().name("Java").build()).build();
        SkillVerificationResult resultNullRepos = matcher.matchSkillsToRepositories(List.of(claim), null);
        assertThat(resultNullRepos.getSkills()).hasSize(1);
        assertThat(resultNullRepos.getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("GitHubTool: Filters out fork repositories and returns non-forks")
    void testGitHubTool_FiltersForks() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        GitHubTool tool = new GitHubTool(mockRestTemplate, null);

        GitHubRepoDto ownRepo = GitHubRepoDto.builder()
                .name("my-project")
                .htmlUrl("https://github.com/testuser/my-project")
                .fork(false)
                .build();

        GitHubRepoDto forkedRepo = GitHubRepoDto.builder()
                .name("other-project")
                .htmlUrl("https://github.com/testuser/other-project")
                .fork(true)
                .build();

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq("testuser")
        )).thenReturn(ResponseEntity.ok(List.of(ownRepo, forkedRepo)));

        List<GitHubRepoDto> result = tool.fetchPublicRepositories("testuser");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("my-project");
    }

    @Test
    @DisplayName("GitHubTool: Handles 404 gracefully by returning empty list")
    void testGitHubTool_Handles404() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        GitHubTool tool = new GitHubTool(mockRestTemplate, null);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq("nonexistentuser")
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

        List<GitHubRepoDto> result = tool.fetchPublicRepositories("nonexistentuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("GitHubTool: Handles 403 / 429 rate limit gracefully")
    void testGitHubTool_HandlesRateLimit() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        GitHubTool tool = new GitHubTool(mockRestTemplate, null);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq("ratelimiteduser")
        )).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "API rate limit exceeded"));

        List<GitHubRepoDto> result = tool.fetchPublicRepositories("ratelimiteduser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("GitHubTool: Handles timeout gracefully")
    void testGitHubTool_HandlesTimeout() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        GitHubTool tool = new GitHubTool(mockRestTemplate, null);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq("timeoutuser")
        )).thenThrow(new ResourceAccessException("Connection timed out"));

        List<GitHubRepoDto> result = tool.fetchPublicRepositories("timeoutuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("GitHubTool: Caches results for repeated queries")
    void testGitHubTool_CachesResults() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        GitHubTool tool = new GitHubTool(mockRestTemplate, null);

        GitHubRepoDto repo = GitHubRepoDto.builder().name("cached-repo").fork(false).build();
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq("cacheuser")
        )).thenReturn(ResponseEntity.ok(List.of(repo)));

        List<GitHubRepoDto> firstCall = tool.fetchPublicRepositories("cacheuser");
        List<GitHubRepoDto> secondCall = tool.fetchPublicRepositories("cacheuser");

        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(1);
        verify(mockRestTemplate, times(1)).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class), anyString());
    }

    @Test
    @DisplayName("ProofOfSkillAgent: Executes and returns enriched skill explanations")
    void testProofOfSkillAgentExecution() {
        ProfileSkill javaClaim = ProfileSkill.builder()
                .skill(Skill.builder().name("java").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        GitHubRepoDto repo = GitHubRepoDto.builder()
                .name("ecommerce-api")
                .htmlUrl("https://github.com/testuser/ecommerce-api")
                .language("Java")
                .fork(false)
                .build();

        SkillVerificationResult precomputed = matcher.matchSkillsToRepositories(List.of(javaClaim), List.of(repo));

        AgentContext<SkillVerificationResult> ctx = AgentContext.<SkillVerificationResult>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(precomputed)
                .targetRole("Backend Engineer")
                .build();

        AgentResult<SkillVerificationResult> result = proofOfSkillAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getOverallVerifiedPercentage()).isEqualTo(100.0);
        assertThat(result.getData().getSkills()).hasSize(1);
        assertThat(result.getData().getSkills().get(0).getSkillName()).isEqualTo("java");
        assertThat(result.getData().getSkills().get(0).getStatus()).isEqualTo(SkillVerificationStatus.VERIFIED);
    }
}
