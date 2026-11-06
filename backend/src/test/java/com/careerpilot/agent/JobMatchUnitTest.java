package com.careerpilot.agent;

import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.agent.jobmatch.JobMatchCalculator;
import com.careerpilot.dto.jobmatch.JobMatchComputationResult;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobMatchUnitTest {

    private JobMatchCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new JobMatchCalculator(null, null);
    }

    @Test
    @DisplayName("Deterministic: Extracts required skills using word-boundary matching without false positives")
    void testExtractSkillsFromJd() {
        String jd = "Looking for a Go developer with Docker and Spring Boot skills. Must not be bad at coding.";

        Map<String, String> catalog = Map.of(
                "Go", "Core Language",
                "Docker", "DevOps",
                "Spring Boot", "Framework",
                "Spring", "Framework"
        );

        Map<String, String> extracted = calculator.extractSkillsFromJd(jd, catalog);

        assertThat(extracted).containsKey("Go");
        assertThat(extracted).containsKey("Docker");
        assertThat(extracted).containsKey("Spring Boot");
        // "Spring" should not be duplicated when "Spring Boot" matches
        assertThat(extracted).doesNotContainKey("Spring");
    }

    @Test
    @DisplayName("Deterministic: Extracts experience patterns correctly")
    void testExtractExperienceFromJd() {
        assertThat(calculator.extractExperienceFromJd("Requires 3+ years of experience in Java"))
                .isEqualTo("3+ years");
        assertThat(calculator.extractExperienceFromJd("Minimum 5 years experience with backend systems"))
                .isEqualTo("5 years");
        assertThat(calculator.extractExperienceFromJd("Looking for 2-4 years of industry experience"))
                .isEqualTo("2-4 years");
        assertThat(calculator.extractExperienceFromJd("No specific tenure required"))
                .isEqualTo("Not specified");
    }

    @Test
    @DisplayName("Deterministic: Calculates match percentage with verified and missing skills")
    void testCalculateJobMatch() {
        String jd = "We need a developer with Java, Docker, and PostgreSQL experience with 2+ years of experience.";

        ProfileSkill javaSkill = ProfileSkill.builder()
                .skill(Skill.builder().name("Java").build())
                .claimedLevel("ADVANCED")
                .build();

        ProfileSkill postgresSkill = ProfileSkill.builder()
                .skill(Skill.builder().name("PostgreSQL").build())
                .claimedLevel("INTERMEDIATE")
                .build();

        List<ProfileSkill> claimedSkills = List.of(javaSkill, postgresSkill);

        // Java is VERIFIED, PostgreSQL is UNVERIFIED, Docker is missing
        Map<String, SkillVerificationStatus> statusMap = Map.of(
                "java", SkillVerificationStatus.VERIFIED
        );

        JobMatchComputationResult result = calculator.calculateJobMatch(
                jd, "Backend Engineer", "Acme", claimedSkills, statusMap
        );

        assertThat(result.getExtractedExperience()).isEqualTo("2+ years");
        assertThat(result.getMatchedSkills()).hasSize(2);
        assertThat(result.getMissingSkills()).hasSize(1);
        assertThat(result.getMissingSkills().get(0).getSkillName()).isEqualTo("Docker");

        // 3 skills total: Java (VERIFIED = 1.0), PostgreSQL (UNVERIFIED = 0.6), Docker (missing = 0.0)
        // Score = (1.0 + 0.6) / 3 * 100 = 53.3%
        assertThat(result.getMatchPercentage()).isEqualTo(53.3);
    }
}
