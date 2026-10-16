package com.careerpilot.agent.skillgap;

import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.dto.skillgap.*;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.RoleRequiredSkill;
import com.careerpilot.entity.TargetRole;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SkillGapCalculator {

    /**
     * Computes the deterministic gap between a candidate's profile skills and a target role.
     * Identifies matched skills, missing skills, and weak (claimed but UNVERIFIED) skills.
     */
    public SkillGapComputationResult calculateGap(
            TargetRole role,
            List<ProfileSkill> userProfileSkills,
            Map<String, SkillVerificationStatus> verifiedStatusMap
    ) {
        if (role == null) {
            throw new IllegalArgumentException("Target role cannot be null");
        }

        List<RoleRequiredSkill> requiredSkills = (role.getRequiredSkills() != null)
                ? role.getRequiredSkills()
                : Collections.emptyList();

        Map<String, ProfileSkill> userSkillsByNormName = new HashMap<>();
        if (userProfileSkills != null) {
            for (ProfileSkill ps : userProfileSkills) {
                if (ps != null && ps.getSkill() != null && ps.getSkill().getName() != null) {
                    userSkillsByNormName.put(normalize(ps.getSkill().getName()), ps);
                }
            }
        }

        Map<String, SkillVerificationStatus> safeStatusMap = (verifiedStatusMap != null)
                ? verifiedStatusMap
                : Collections.emptyMap();

        List<MatchedSkillDto> matched = new ArrayList<>();
        List<MissingSkillDto> missing = new ArrayList<>();
        List<WeakSkillDto> weak = new ArrayList<>();

        double totalWeight = 0.0;
        double earnedWeight = 0.0;

        for (RoleRequiredSkill req : requiredSkills) {
            if (req == null || req.getSkillName() == null) continue;

            String reqNorm = normalize(req.getSkillName());
            int weight = req.getWeight() != null ? req.getWeight() : 1;
            totalWeight += weight;

            ProfileSkill matchedPs = findMatchingUserSkill(reqNorm, userSkillsByNormName);

            if (matchedPs != null) {
                String claimedLevel = matchedPs.getClaimedLevel() != null ? matchedPs.getClaimedLevel() : "INTERMEDIATE";
                SkillVerificationStatus verification = findVerificationStatus(reqNorm, safeStatusMap);

                if (verification == SkillVerificationStatus.UNVERIFIED) {
                    // Claimed by candidate on profile, but verified against GitHub with zero evidence
                    weak.add(WeakSkillDto.builder()
                            .skillName(req.getSkillName())
                            .claimedLevel(claimedLevel)
                            .reason("Claimed on profile but unverified against public code repositories")
                            .weight(weight)
                            .build());
                    earnedWeight += (weight * 0.5); // Partial credit for claiming it
                } else {
                    String statusStr = (verification != null) ? verification.name() : "CLAIMED";
                    matched.add(MatchedSkillDto.builder()
                            .skillName(req.getSkillName())
                            .claimedLevel(claimedLevel)
                            .verificationStatus(statusStr)
                            .weight(weight)
                            .build());
                    earnedWeight += weight;
                }
            } else {
                missing.add(MissingSkillDto.builder()
                        .skillName(req.getSkillName())
                        .category(req.getCategory() != null ? req.getCategory() : "General")
                        .priority(req.getPriority() != null ? req.getPriority() : "IMPORTANT")
                        .weight(weight)
                        .build());
            }
        }

        double matchPercentage = 0.0;
        if (totalWeight > 0.0) {
            matchPercentage = Math.round((earnedWeight / totalWeight) * 1000.0) / 10.0;
            if (matchPercentage > 100.0) {
                matchPercentage = 100.0;
            }
        } else {
            matchPercentage = 100.0;
        }

        return SkillGapComputationResult.builder()
                .roleId(role.getId())
                .roleName(role.getName())
                .roleDescription(role.getDescription())
                .matchPercentage(matchPercentage)
                .matchedSkills(matched)
                .missingSkills(missing)
                .weakSkills(weak)
                .build();
    }

    private ProfileSkill findMatchingUserSkill(String reqNorm, Map<String, ProfileSkill> userSkills) {
        if (userSkills.containsKey(reqNorm)) {
            return userSkills.get(reqNorm);
        }

        for (Map.Entry<String, ProfileSkill> entry : userSkills.entrySet()) {
            String candidateNorm = entry.getKey();
            if (candidateNorm.contains(reqNorm) || reqNorm.contains(candidateNorm)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private SkillVerificationStatus findVerificationStatus(String reqNorm, Map<String, SkillVerificationStatus> statusMap) {
        if (statusMap.containsKey(reqNorm)) {
            return statusMap.get(reqNorm);
        }
        for (Map.Entry<String, SkillVerificationStatus> entry : statusMap.entrySet()) {
            String k = entry.getKey();
            if (k.contains(reqNorm) || reqNorm.contains(k)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
