package com.careerpilot.agent.jobmatch;

import com.careerpilot.agent.dto.SkillVerificationStatus;
import com.careerpilot.agent.skillgap.SkillGapCalculator;
import com.careerpilot.dto.jobmatch.*;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.RoleRequiredSkill;
import com.careerpilot.entity.Skill;
import com.careerpilot.repository.RoleRequiredSkillRepository;
import com.careerpilot.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchCalculator {

    private final SkillRepository skillRepository;
    private final RoleRequiredSkillRepository roleRequiredSkillRepository;

    // Standard Curated Tech Skills Catalog (Name -> Category)
    private static final Map<String, String> DEFAULT_SKILL_CATALOG = new LinkedHashMap<>();

    static {
        // Languages
        register("Java", "Core Language");
        register("Python", "Core Language");
        register("JavaScript", "Core Language");
        register("TypeScript", "Core Language");
        register("C++", "Core Language");
        register("C#", "Core Language");
        register("Go", "Core Language");
        register("Golang", "Core Language");
        register("Rust", "Core Language");
        register("Ruby", "Core Language");
        register("PHP", "Core Language");
        register("SQL", "Database");
        register("HTML", "Frontend");
        register("CSS", "Frontend");
        register("Kotlin", "Mobile / Backend");
        register("Swift", "Mobile");
        register("Scala", "Core Language");

        // Frameworks & Libraries
        register("Spring Boot", "Framework");
        register("Spring Framework", "Framework");
        register("Spring", "Framework");
        register("React", "Frontend");
        register("Next.js", "Frontend");
        register("Angular", "Frontend");
        register("Vue", "Frontend");
        register("Vue.js", "Frontend");
        register("Node.js", "Backend");
        register("Express", "Backend");
        register("Django", "Framework");
        register("Flask", "Framework");
        register("FastAPI", "Framework");
        register("ASP.NET", "Framework");
        register("Hibernate", "Framework");
        register("JPA", "Framework");
        register("JUnit", "Testing");
        register("Mockito", "Testing");
        register("Redux", "Frontend");
        register("Tailwind CSS", "Frontend");

        // Databases & Caches
        register("PostgreSQL", "Database");
        register("MySQL", "Database");
        register("MongoDB", "Database");
        register("Redis", "Database");
        register("Cassandra", "Database");
        register("Oracle", "Database");
        register("SQLite", "Database");
        register("DynamoDB", "Database");
        register("Elasticsearch", "Database");

        // Cloud & DevOps
        register("Docker", "DevOps");
        register("Kubernetes", "DevOps");
        register("AWS", "Cloud");
        register("Azure", "Cloud");
        register("GCP", "Cloud");
        register("CI/CD", "DevOps");
        register("Git", "Tooling");
        register("GitHub Actions", "DevOps");
        register("Terraform", "DevOps");
        register("Linux", "Tooling");
        register("Jenkins", "DevOps");

        // Architecture & Concepts
        register("REST APIs", "Architecture");
        register("RESTful APIs", "Architecture");
        register("REST", "Architecture");
        register("Microservices", "Architecture");
        register("GraphQL", "Architecture");
        register("Kafka", "Messaging");
        register("RabbitMQ", "Messaging");
        register("WebSockets", "Architecture");
        register("gRPC", "Architecture");
        register("System Design", "Architecture");
        register("Agile", "Methodology");
        register("Scrum", "Methodology");
        register("Unit Testing", "Testing");
    }

    private static void register(String skill, String category) {
        DEFAULT_SKILL_CATALOG.put(skill, category);
    }

    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile(
            "(?i)(\\d+\\+?(?:\\s*(?:-|to)\\s*\\d+\\+?)?)\\s*(?:years?|yrs?)(?:\\s+(?:of\\s+)?(?:experience|exp))?"
    );

    /**
     * Deterministically extracts skills & experience from the job description
     * and evaluates alignment with candidate profile and Proof-of-Skill status.
     */
    public JobMatchComputationResult calculateJobMatch(
            String jobDescription,
            String jobTitle,
            String companyName,
            List<ProfileSkill> claimedSkills,
            Map<String, SkillVerificationStatus> verifiedStatusMap
    ) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return JobMatchComputationResult.builder()
                    .jobTitle(jobTitle)
                    .companyName(companyName)
                    .jobDescription("")
                    .extractedExperience("Not specified")
                    .matchPercentage(0.0)
                    .matchedSkills(Collections.emptyList())
                    .missingSkills(Collections.emptyList())
                    .build();
        }

        // 1. Build consolidated skill catalog (combining DB skills and default catalog)
        Map<String, String> catalog = getConsolidatedCatalog();

        // 2. Extract required skills from JD using word-boundary matching, longest keywords first
        Map<String, String> extractedSkills = extractSkillsFromJd(jobDescription, catalog);

        // 3. Extract required experience string
        String extractedExperience = extractExperienceFromJd(jobDescription);

        // 4. Map candidate's claimed skills
        Map<String, ProfileSkill> userSkillsByNorm = new HashMap<>();
        if (claimedSkills != null) {
            for (ProfileSkill ps : claimedSkills) {
                if (ps != null && ps.getSkill() != null && ps.getSkill().getName() != null) {
                    userSkillsByNorm.put(SkillGapCalculator.normalize(ps.getSkill().getName()), ps);
                }
            }
        }

        Map<String, SkillVerificationStatus> safeStatusMap = (verifiedStatusMap != null)
                ? verifiedStatusMap
                : Collections.emptyMap();

        List<JobMatchedSkillDto> matched = new ArrayList<>();
        List<JobMissingSkillDto> missing = new ArrayList<>();
        double earnedScore = 0.0;

        for (Map.Entry<String, String> entry : extractedSkills.entrySet()) {
            String skillName = entry.getKey();
            String category = entry.getValue();
            String normSkill = SkillGapCalculator.normalize(skillName);

            ProfileSkill matchedPs = findMatchingUserSkill(normSkill, userSkillsByNorm);

            if (matchedPs != null) {
                String claimedLevel = matchedPs.getClaimedLevel() != null
                        ? matchedPs.getClaimedLevel()
                        : "INTERMEDIATE";

                SkillVerificationStatus verification = findVerificationStatus(normSkill, safeStatusMap);
                String statusStr = (verification != null) ? verification.name() : "UNVERIFIED";

                matched.add(JobMatchedSkillDto.builder()
                        .skillName(skillName)
                        .claimedLevel(claimedLevel)
                        .verificationStatus(statusStr)
                        .build());

                // Weighted score: VERIFIED = 1.0, PARTIAL = 0.8, UNVERIFIED = 0.6
                if (verification == SkillVerificationStatus.VERIFIED) {
                    earnedScore += 1.0;
                } else if (verification == SkillVerificationStatus.PARTIAL) {
                    earnedScore += 0.8;
                } else {
                    earnedScore += 0.6;
                }
            } else {
                missing.add(JobMissingSkillDto.builder()
                        .skillName(skillName)
                        .category(category)
                        .build());
            }
        }

        // 5. Calculate match percentage
        int totalExtracted = matched.size() + missing.size();
        double matchPercentage = 0.0;
        if (totalExtracted > 0) {
            matchPercentage = Math.round((earnedScore / totalExtracted) * 1000.0) / 10.0;
            if (matchPercentage > 100.0) {
                matchPercentage = 100.0;
            }
        }

        return JobMatchComputationResult.builder()
                .jobTitle(jobTitle)
                .companyName(companyName)
                .jobDescription(jobDescription)
                .extractedExperience(extractedExperience)
                .matchPercentage(matchPercentage)
                .matchedSkills(matched)
                .missingSkills(missing)
                .build();
    }

    public Map<String, String> extractSkillsFromJd(String text, Map<String, String> catalog) {
        Map<String, String> extracted = new LinkedHashMap<>();
        if (text == null || text.isBlank() || catalog == null) {
            return extracted;
        }

        // Sort catalog keys by length descending to match composite phrases first (e.g., "Spring Boot" before "Spring")
        List<String> sortedKeywords = new ArrayList<>(catalog.keySet());
        sortedKeywords.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // Work with a mutable copy so we can mask matched regions to prevent sub-keywords matching the same text span
        StringBuilder workingText = new StringBuilder(text);

        for (String keyword : sortedKeywords) {
            // Word-boundary regex avoiding prefix/suffix alphanumeric collision
            String regex = "(?i)(?:^|(?<=[^a-zA-Z0-9+#./-]))" + Pattern.quote(keyword) + "(?=[^a-zA-Z0-9+#./-]|$|\n|\r)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(workingText);

            boolean found = false;
            while (matcher.find()) {
                found = true;
                int start = matcher.start();
                int end = matcher.end();
                for (int i = start; i < end; i++) {
                    workingText.setCharAt(i, ' ');
                }
                matcher = pattern.matcher(workingText);
            }

            if (found) {
                extracted.put(keyword, catalog.get(keyword));
            }
        }

        return extracted;
    }

    public String extractExperienceFromJd(String text) {
        if (text == null || text.isBlank()) {
            return "Not specified";
        }

        Matcher matcher = EXPERIENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            String val = matcher.group(1).trim();
            if (!val.toLowerCase().contains("year")) {
                val = val + " years";
            }
            return val;
        }

        return "Not specified";
    }

    private Map<String, String> getConsolidatedCatalog() {
        Map<String, String> catalog = new LinkedHashMap<>(DEFAULT_SKILL_CATALOG);

        try {
            if (skillRepository != null) {
                List<Skill> dbSkills = skillRepository.findAll();
                for (Skill s : dbSkills) {
                    if (s != null && s.getName() != null && !s.getName().isBlank()) {
                        String cat = s.getCategory() != null ? s.getCategory() : "Technical";
                        catalog.putIfAbsent(s.getName().trim(), cat);
                    }
                }
            }

            if (roleRequiredSkillRepository != null) {
                List<RoleRequiredSkill> roleSkills = roleRequiredSkillRepository.findAll();
                for (RoleRequiredSkill rs : roleSkills) {
                    if (rs != null && rs.getSkillName() != null && !rs.getSkillName().isBlank()) {
                        String cat = rs.getCategory() != null ? rs.getCategory() : "Role Required";
                        catalog.putIfAbsent(rs.getSkillName().trim(), cat);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query DB skills for job match catalog: {}", e.getMessage());
        }

        return catalog;
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
}
