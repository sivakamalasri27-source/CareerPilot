package com.careerpilot.agent.skill;

import com.careerpilot.agent.dto.RepoEvidence;
import com.careerpilot.agent.dto.SkillEvidence;
import com.careerpilot.agent.dto.SkillVerificationResult;
import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.tool.github.GitHubRepoDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SkillEvidenceMatcher {

    /**
     * Maps claimed skills to public repository evidence in deterministic Java logic.
     * Classifies as VERIFIED (multiple repos or primary language match),
     * PARTIAL (single mention or weak evidence), or UNVERIFIED (zero matches).
     */
    public SkillVerificationResult matchSkillsToRepositories(
            List<ProfileSkill> claimedSkills,
            List<GitHubRepoDto> repositories
    ) {
        if (claimedSkills == null || claimedSkills.isEmpty()) {
            return SkillVerificationResult.builder()
                    .overallVerifiedPercentage(0.0)
                    .skills(Collections.emptyList())
                    .summary("No claimed skills configured on profile.")
                    .build();
        }

        List<GitHubRepoDto> safeRepos = (repositories != null) ? repositories : Collections.emptyList();
        List<SkillEvidence> skillEvidenceList = new ArrayList<>();

        int verifiedCount = 0;
        int partialCount = 0;

        for (ProfileSkill ps : claimedSkills) {
            String skillName = (ps.getSkill() != null && ps.getSkill().getName() != null)
                    ? ps.getSkill().getName().trim()
                    : "Unknown";
            String claimedLevel = ps.getClaimedLevel() != null ? ps.getClaimedLevel() : "INTERMEDIATE";

            List<RepoEvidence> evidences = findEvidenceForSkill(skillName, safeRepos);

            SkillVerificationStatus status;
            boolean hasPrimaryLanguage = evidences.stream().anyMatch(e -> e.getReason().startsWith("Primary language"));

            if (evidences.size() >= 2 || (evidences.size() == 1 && hasPrimaryLanguage)) {
                status = SkillVerificationStatus.VERIFIED;
                verifiedCount++;
            } else if (evidences.size() == 1) {
                status = SkillVerificationStatus.PARTIAL;
                partialCount++;
            } else {
                status = SkillVerificationStatus.UNVERIFIED;
            }

            String defaultExplanation = createDefaultExplanation(skillName, status, evidences);

            skillEvidenceList.add(SkillEvidence.builder()
                    .skillName(skillName)
                    .claimedLevel(claimedLevel)
                    .status(status)
                    .evidence(evidences)
                    .explanation(defaultExplanation)
                    .build());
        }

        double percentage = (claimedSkills.size() > 0)
                ? Math.round(((double) verifiedCount / claimedSkills.size()) * 100.0)
                : 0.0;

        String summary = String.format("Verified %d of %d claimed skills (%d partial, %d unverified) against public GitHub activity.",
                verifiedCount, claimedSkills.size(), partialCount, claimedSkills.size() - verifiedCount - partialCount);

        return SkillVerificationResult.builder()
                .overallVerifiedPercentage(percentage)
                .skills(skillEvidenceList)
                .summary(summary)
                .build();
    }

    private List<RepoEvidence> findEvidenceForSkill(String skillName, List<GitHubRepoDto> repos) {
        List<RepoEvidence> matches = new ArrayList<>();
        String normalizedSkill = normalize(skillName);

        for (GitHubRepoDto repo : repos) {
            if (repo == null) continue;

            String repoName = repo.getName() != null ? repo.getName() : "untitled";
            String repoUrl = repo.getHtmlUrl() != null ? repo.getHtmlUrl() : "";
            boolean matched = false;

            // 1. Language match
            if (repo.getLanguage() != null && normalize(repo.getLanguage()).equals(normalizedSkill)) {
                matches.add(RepoEvidence.builder()
                        .repoName(repoName)
                        .repoUrl(repoUrl)
                        .reason("Primary language: " + repo.getLanguage())
                        .build());
                matched = true;
            }

            // 2. Topic tag match
            if (!matched && repo.getTopics() != null) {
                for (String topic : repo.getTopics()) {
                    if (topic != null && normalize(topic).contains(normalizedSkill)) {
                        matches.add(RepoEvidence.builder()
                                .repoName(repoName)
                                .repoUrl(repoUrl)
                                .reason("Topic tag: " + topic)
                                .build());
                        matched = true;
                        break;
                    }
                }
            }

            // 3. Name or Description keyword match
            if (!matched) {
                String desc = repo.getDescription() != null ? repo.getDescription() : "";
                String name = repo.getName() != null ? repo.getName() : "";
                if (containsWord(name, normalizedSkill) || containsWord(desc, normalizedSkill)) {
                    matches.add(RepoEvidence.builder()
                            .repoName(repoName)
                            .repoUrl(repoUrl)
                            .reason("Referenced in repository name or description")
                            .build());
                }
            }
        }

        return matches;
    }

    private boolean containsWord(String text, String normalizedKeyword) {
        if (text == null || text.isBlank() || normalizedKeyword.isBlank()) {
            return false;
        }
        return normalize(text).contains(normalizedKeyword);
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String createDefaultExplanation(String skill, SkillVerificationStatus status, List<RepoEvidence> evidences) {
        if (status == SkillVerificationStatus.VERIFIED) {
            return String.format("Verified skill across %d public repository references, including %s.",
                    evidences.size(), evidences.get(0).getRepoName());
        } else if (status == SkillVerificationStatus.PARTIAL) {
            return String.format("Partially verified through 1 repository reference (%s). Additional project evidence recommended.",
                    evidences.get(0).getRepoName());
        } else {
            return String.format("No evidence found in public repositories for '%s'. Building a dedicated project showcasing this skill is recommended.", skill);
        }
    }
}
