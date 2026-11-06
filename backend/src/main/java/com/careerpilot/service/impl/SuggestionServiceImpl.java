package com.careerpilot.service.impl;

import com.careerpilot.agent.dto.ParsedEducationDto;
import com.careerpilot.agent.dto.ParsedProjectDto;
import com.careerpilot.agent.dto.ParsedSkillDto;
import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.*;
import com.careerpilot.exception.ConflictException;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.*;
import com.careerpilot.service.SuggestionService;
import com.careerpilot.util.SanitizationUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final AgentSuggestionRepository agentSuggestionRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final EducationRepository educationRepository;
    private final SkillRepository skillRepository;
    private final ProfileSkillRepository profileSkillRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SuggestionResponse> getSuggestions(User user, SuggestionStatus status, Pageable pageable) {
        Profile profile = resolveProfile(user);

        Page<AgentSuggestion> page;
        if (status != null) {
            page = agentSuggestionRepository.findAllByProfileIdAndStatus(profile.getId(), status, pageable);
        } else {
            page = agentSuggestionRepository.findAllByProfileId(profile.getId(), pageable);
        }

        List<SuggestionResponse> responseList = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return PageResponse.<SuggestionResponse>builder()
                .content(responseList)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestionResponse getSuggestionById(User user, Long id) {
        Profile profile = resolveProfile(user);
        AgentSuggestion suggestion = agentSuggestionRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found with id: " + id));
        return mapToResponse(suggestion);
    }

    @Override
    @Transactional
    public SuggestionResponse approveSuggestion(User user, Long id, ApproveSuggestionRequest request) {
        Profile profile = resolveProfile(user);
        AgentSuggestion suggestion = agentSuggestionRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found with id: " + id));

        // 1. Guard against double-decision (409)
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ConflictException("Suggestion has already been decided: " + suggestion.getStatus());
        }

        // 2. Perform stale check and apply changes based on target section
        String targetSection = suggestion.getTargetSection();
        String textToApply = (request != null && request.getEditedText() != null && !request.getEditedText().isBlank())
                ? request.getEditedText().trim()
                : suggestion.getSuggestedText();

        if ("PROJECT_BULLETS".equalsIgnoreCase(targetSection) || "PROJECT".equalsIgnoreCase(targetSection)) {
            if (suggestion.getTargetId() == null) {
                applyNewProject(profile, suggestion, textToApply);
            } else {
                applyProjectBullets(profile, suggestion, textToApply);
            }
        } else if ("SKILL".equalsIgnoreCase(targetSection)) {
            applySkill(profile, suggestion, textToApply);
        } else if ("EDUCATION".equalsIgnoreCase(targetSection)) {
            applyEducation(profile, suggestion, textToApply);
        } else if ("HEADLINE".equalsIgnoreCase(targetSection)) {
            applyHeadline(profile, suggestion, textToApply);
        } else if ("ABOUT".equalsIgnoreCase(targetSection)) {
            applyAbout(profile, suggestion, textToApply);
        } else {
            log.warn("Unrecognized target section for suggestion {}: {}", id, targetSection);
            throw new IllegalArgumentException("Unsupported target section: " + targetSection);
        }

        // 3. Mark suggestion APPROVED
        suggestion.setStatus(SuggestionStatus.APPROVED);
        suggestion.setDecidedAt(Instant.now());
        AgentSuggestion saved = agentSuggestionRepository.save(suggestion);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SuggestionResponse rejectSuggestion(User user, Long id) {
        Profile profile = resolveProfile(user);
        AgentSuggestion suggestion = agentSuggestionRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found with id: " + id));

        // Guard against double-decision (409)
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ConflictException("Suggestion has already been decided: " + suggestion.getStatus());
        }

        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setDecidedAt(Instant.now());
        AgentSuggestion saved = agentSuggestionRepository.save(suggestion);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AgentSuggestion createSuggestion(
            Profile profile,
            String agentName,
            String targetSection,
            Long targetId,
            String originalText,
            String suggestedText,
            String rationale
    ) {
        String originalTextHash = computeHash(originalText);

        AgentSuggestion suggestion = AgentSuggestion.builder()
                .profile(profile)
                .agentName(agentName)
                .targetSection(targetSection)
                .targetId(targetId)
                .originalText(originalText)
                .originalTextHash(originalTextHash)
                .suggestedText(suggestedText)
                .rationale(rationale)
                .status(SuggestionStatus.PENDING)
                .build();

        return agentSuggestionRepository.save(suggestion);
    }

    @Override
    public SuggestionResponse mapToResponse(AgentSuggestion suggestion) {
        List<String> bullets = parseBullets(suggestion.getSuggestedText());

        return SuggestionResponse.builder()
                .id(suggestion.getId())
                .profileId(suggestion.getProfile() != null ? suggestion.getProfile().getId() : null)
                .agentName(suggestion.getAgentName())
                .targetSection(suggestion.getTargetSection())
                .targetId(suggestion.getTargetId())
                .originalText(suggestion.getOriginalText())
                .suggestedText(suggestion.getSuggestedText())
                .suggestedBullets(bullets)
                .rationale(suggestion.getRationale())
                .status(suggestion.getStatus())
                .createdAt(suggestion.getCreatedAt())
                .decidedAt(suggestion.getDecidedAt())
                .build();
    }

    private void applyNewProject(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (textToApply == null || textToApply.isBlank()) {
            throw new IllegalArgumentException("Project content cannot be blank");
        }

        ParsedProjectDto projectDto;
        try {
            projectDto = objectMapper.readValue(textToApply.trim(), ParsedProjectDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse project suggestion JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Malformed project suggestion JSON payload");
        }

        if (projectDto == null) {
            throw new IllegalArgumentException("Malformed project suggestion JSON payload");
        }

        String title = SanitizationUtil.sanitize(projectDto.getTitle());
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Project title is required");
        }
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }

        String description = SanitizationUtil.sanitize(projectDto.getDescription());
        String role = SanitizationUtil.sanitize(projectDto.getRole());
        if (role != null && role.length() > 100) {
            role = role.substring(0, 100);
        }

        String technologies = SanitizationUtil.sanitize(projectDto.getTechnologies());
        if (technologies != null && technologies.length() > 255) {
            technologies = technologies.substring(0, 255);
        }

        List<String> rawBullets = projectDto.getBullets() != null ? projectDto.getBullets() : Collections.emptyList();
        List<String> cleanBullets = rawBullets.stream()
                .map(SanitizationUtil::sanitize)
                .filter(b -> b != null && !b.isBlank())
                .toList();

        if (cleanBullets.size() > 8) {
            throw new IllegalArgumentException("Maximum of 8 project bullets allowed");
        }
        for (String bullet : cleanBullets) {
            if (bullet.length() > 300) {
                throw new IllegalArgumentException("Project bullet must not exceed 300 characters: " + bullet);
            }
        }

        Project newProject = Project.builder()
                .profile(profile)
                .title(title)
                .description(description)
                .role(role)
                .technologies(technologies)
                .bullets(new ArrayList<>(cleanBullets))
                .isAcademic(true)
                .build();

        projectRepository.save(newProject);
        log.info("Created new project '{}' (profile ID: {}) from suggestion ID: {}", title, profile.getId(), suggestion.getId());
    }

    private void applyProjectBullets(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (suggestion.getTargetId() == null) {
            throw new IllegalArgumentException("Cannot apply project bullets suggestion without a target project ID");
        }

        Project project = projectRepository.findByIdAndProfileId(suggestion.getTargetId(), profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Target project not found with id: " + suggestion.getTargetId()));

        // Stale check
        String currentSerialized = serializeBullets(project.getBullets());
        String currentHash = computeHash(currentSerialized);

        if (suggestion.getOriginalTextHash() != null && !suggestion.getOriginalTextHash().equals(currentHash)) {
            throw new ConflictException("Target project content has been modified since this suggestion was created. Suggestion is stale.");
        }

        // Validate and parse new bullets
        List<String> parsedBullets = parseBullets(textToApply);
        if (parsedBullets.isEmpty()) {
            throw new IllegalArgumentException("Project bullets cannot be empty");
        }
        if (parsedBullets.size() > 8) {
            throw new IllegalArgumentException("Maximum of 8 project bullets allowed");
        }
        for (String bullet : parsedBullets) {
            if (bullet.length() > 300) {
                throw new IllegalArgumentException("Project bullet must not exceed 300 characters: " + bullet);
            }
        }

        project.setBullets(new ArrayList<>(parsedBullets));
        projectRepository.save(project);
    }

    private void applySkill(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (textToApply == null || textToApply.isBlank()) {
            throw new IllegalArgumentException("Skill content cannot be blank");
        }

        String skillName;
        String proficiency;

        String trimmed = textToApply.trim();
        if (trimmed.startsWith("{")) {
            try {
                ParsedSkillDto skillDto = objectMapper.readValue(trimmed, ParsedSkillDto.class);
                if (skillDto == null) {
                    throw new IllegalArgumentException("Malformed skill suggestion JSON payload");
                }
                skillName = skillDto.getName();
                proficiency = skillDto.getProficiency();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to parse skill suggestion JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Malformed skill suggestion JSON payload");
            }
        } else {
            int openParen = trimmed.indexOf('(');
            int closeParen = trimmed.lastIndexOf(')');
            if (openParen > 0 && closeParen > openParen) {
                skillName = trimmed.substring(0, openParen).trim();
                proficiency = trimmed.substring(openParen + 1, closeParen).trim();
            } else {
                skillName = trimmed;
                proficiency = "INTERMEDIATE";
            }
        }

        skillName = SanitizationUtil.sanitize(skillName);
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (skillName.length() > 100) {
            skillName = skillName.substring(0, 100);
        }

        if (proficiency == null || proficiency.isBlank()) {
            proficiency = "INTERMEDIATE";
        } else {
            proficiency = proficiency.toUpperCase().trim();
        }

        // Skip if duplicate (Requirement 1: skill suggestion -> create/link the skill (skip if duplicate))
        if (profileSkillRepository.existsByProfileIdAndSkillNameIgnoreCase(profile.getId(), skillName)) {
            log.info("Skill '{}' already exists on profile ID: {}. Skipping duplicate.", skillName, profile.getId());
            return;
        }

        final String finalSkillName = skillName;
        Skill skill = skillRepository.findByNameIgnoreCase(finalSkillName)
                .orElseGet(() -> skillRepository.save(Skill.builder()
                        .name(finalSkillName)
                        .category("TECHNICAL")
                        .build()));

        ProfileSkill profileSkill = ProfileSkill.builder()
                .profile(profile)
                .skill(skill)
                .claimedLevel(proficiency)
                .verificationStatus(VerificationStatus.UNVERIFIED)
                .build();

        profileSkillRepository.save(profileSkill);
        log.info("Linked skill '{}' ({}) to profile ID: {} from suggestion ID: {}", skillName, proficiency, profile.getId(), suggestion.getId());
    }

    private void applyEducation(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (textToApply == null || textToApply.isBlank()) {
            throw new IllegalArgumentException("Education content cannot be blank");
        }

        ParsedEducationDto eduDto;
        try {
            eduDto = objectMapper.readValue(textToApply.trim(), ParsedEducationDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse education suggestion JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Malformed education suggestion JSON payload");
        }

        if (eduDto == null) {
            throw new IllegalArgumentException("Malformed education suggestion JSON payload");
        }

        String institution = SanitizationUtil.sanitize(eduDto.getInstitution());
        if (institution == null || institution.isBlank()) {
            throw new IllegalArgumentException("Institution is required");
        }
        if (institution.length() > 255) {
            institution = institution.substring(0, 255);
        }

        String degree = SanitizationUtil.sanitize(eduDto.getDegree());
        if (degree == null || degree.isBlank()) {
            throw new IllegalArgumentException("Degree is required");
        }
        if (degree.length() > 255) {
            degree = degree.substring(0, 255);
        }

        String fieldOfStudy = SanitizationUtil.sanitize(eduDto.getFieldOfStudy());
        if (fieldOfStudy != null && fieldOfStudy.length() > 255) {
            fieldOfStudy = fieldOfStudy.substring(0, 255);
        }

        String gradeOrGpa = SanitizationUtil.sanitize(eduDto.getGradeOrGpa());
        if (gradeOrGpa != null && gradeOrGpa.length() > 50) {
            gradeOrGpa = gradeOrGpa.substring(0, 50);
        }

        Education education = Education.builder()
                .profile(profile)
                .institution(institution)
                .degree(degree)
                .fieldOfStudy(fieldOfStudy)
                .gradeOrGpa(gradeOrGpa)
                .build();

        educationRepository.save(education);
        log.info("Created education record '{}' at '{}' for profile ID: {} from suggestion ID: {}",
                degree, institution, profile.getId(), suggestion.getId());
    }

    private void applyHeadline(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (suggestion.getTargetId() != null || (suggestion.getOriginalText() != null && !suggestion.getOriginalText().isBlank())) {
            String currentHeadline = profile.getHeadline() != null ? profile.getHeadline().trim() : "";
            String currentHash = computeHash(currentHeadline);

            if (suggestion.getOriginalTextHash() != null && !suggestion.getOriginalTextHash().isBlank() && !suggestion.getOriginalTextHash().equals(currentHash)) {
                throw new ConflictException("Target headline has been modified since this suggestion was created. Suggestion is stale.");
            }
        }

        String headline = SanitizationUtil.sanitize(textToApply);
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("Headline cannot be blank");
        }
        if (headline.length() > 120) {
            throw new IllegalArgumentException("Headline must not exceed 120 characters");
        }

        profile.setHeadline(headline);
        profileRepository.save(profile);
    }

    private void applyAbout(Profile profile, AgentSuggestion suggestion, String textToApply) {
        if (suggestion.getTargetId() != null || (suggestion.getOriginalText() != null && !suggestion.getOriginalText().isBlank())) {
            String currentAbout = profile.getAbout() != null ? profile.getAbout().trim() : "";
            String currentHash = computeHash(currentAbout);

            if (suggestion.getOriginalTextHash() != null && !suggestion.getOriginalTextHash().isBlank() && !suggestion.getOriginalTextHash().equals(currentHash)) {
                throw new ConflictException("Target about section has been modified since this suggestion was created. Suggestion is stale.");
            }
        }

        String about = SanitizationUtil.sanitize(textToApply);
        if (about != null && about.length() > 2000) {
            throw new IllegalArgumentException("About section must not exceed 2000 characters");
        }

        profile.setAbout(about);
        profileRepository.save(profile);
    }

    private Profile resolveProfile(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user: " + user.getId()));
    }

    public static String computeHash(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public String serializeBullets(List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(bullets);
        } catch (Exception e) {
            return String.join("\n", bullets);
        }
    }

    @Override
    public List<String> parseBullets(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // fallback to line splitting
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(trimmed);
                if (node.has("bullets") && node.get("bullets").isArray()) {
                    List<String> list = new ArrayList<>();
                    node.get("bullets").forEach(b -> {
                        String txt = b.asText().trim();
                        if (!txt.isBlank()) {
                            list.add(txt);
                        }
                    });
                    return list;
                }
            } catch (Exception ignored) {
                // fallback to line splitting
            }
        }
        return Arrays.stream(trimmed.split("\r?\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[-*•\\d+\\.\\s]+", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }
}
