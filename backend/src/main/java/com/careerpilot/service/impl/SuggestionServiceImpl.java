package com.careerpilot.service.impl;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.*;
import com.careerpilot.exception.ConflictException;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.AgentSuggestionRepository;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.ProjectRepository;
import com.careerpilot.service.SuggestionService;
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
            applyProjectBullets(profile, suggestion, textToApply);
        } else if ("HEADLINE".equalsIgnoreCase(targetSection)) {
            applyHeadline(profile, suggestion, textToApply);
        } else if ("ABOUT".equalsIgnoreCase(targetSection)) {
            applyAbout(profile, suggestion, textToApply);
        } else {
            log.warn("Unrecognized target section for suggestion {}: {}", id, targetSection);
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

    private void applyHeadline(Profile profile, AgentSuggestion suggestion, String textToApply) {
        String currentHeadline = profile.getHeadline() != null ? profile.getHeadline().trim() : "";
        String currentHash = computeHash(currentHeadline);

        if (suggestion.getOriginalTextHash() != null && !suggestion.getOriginalTextHash().equals(currentHash)) {
            throw new ConflictException("Target headline has been modified since this suggestion was created. Suggestion is stale.");
        }

        if (textToApply.isBlank()) {
            throw new IllegalArgumentException("Headline cannot be blank");
        }
        if (textToApply.length() > 120) {
            throw new IllegalArgumentException("Headline must not exceed 120 characters");
        }

        profile.setHeadline(textToApply);
        profileRepository.save(profile);
    }

    private void applyAbout(Profile profile, AgentSuggestion suggestion, String textToApply) {
        String currentAbout = profile.getAbout() != null ? profile.getAbout().trim() : "";
        String currentHash = computeHash(currentAbout);

        if (suggestion.getOriginalTextHash() != null && !suggestion.getOriginalTextHash().equals(currentHash)) {
            throw new ConflictException("Target about section has been modified since this suggestion was created. Suggestion is stale.");
        }

        if (textToApply.length() > 2000) {
            throw new IllegalArgumentException("About section must not exceed 2000 characters");
        }

        profile.setAbout(textToApply);
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
        return Arrays.stream(trimmed.split("\r?\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[-*•\\d+\\.\\s]+", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }
}
