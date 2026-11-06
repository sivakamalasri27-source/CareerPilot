package com.careerpilot.service.impl;

import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.dto.*;
import com.careerpilot.agent.resume.ResumeParserAgent;
import com.careerpilot.dto.resume.ResumeUploadResponse;
import com.careerpilot.dto.resume.ResumeUploadSummaryResponse;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.*;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.ResumeUploadRepository;
import com.careerpilot.service.PdfExtractionService;
import com.careerpilot.service.ResumeService;
import com.careerpilot.service.SuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final PdfExtractionService pdfExtractionService;
    private final ResumeUploadRepository resumeUploadRepository;
    private final ProfileRepository profileRepository;
    private final ResumeParserAgent resumeParserAgent;
    private final SuggestionService suggestionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ResumeUploadResponse uploadAndProcessResume(User user, MultipartFile file) {
        log.info("Processing resume upload for user ID: {}, fileName: {}, size: {} bytes",
                user.getId(), file.getOriginalFilename(), file.getSize());

        // 1. In-memory text extraction & validation (never writes to disk)
        String extractedText = pdfExtractionService.extractText(file);

        // 2. Persist resume upload record (never log the actual resume text)
        ResumeUpload upload = ResumeUpload.builder()
                .user(user)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume.pdf")
                .fileSizeBytes(file.getSize())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/pdf")
                .parsedText(extractedText)
                .status(UploadStatus.PENDING)
                .build();
        upload = resumeUploadRepository.save(upload);

        // 3. Resolve candidate profile
        Profile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(Profile.builder().user(user).build()));

        // 4. Parse resume via ResumeParserAgent
        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(user)
                .profile(profile)
                .input(extractedText)
                .targetRole(profile.getTargetRole())
                .build();

        AgentResult<ParsedResumeResult> parseResult = resumeParserAgent.execute(ctx);
        ParsedResumeResult parsed = parseResult.getData();

        // 5. Convert parsed items to PENDING suggestions (Phase 6 flow), skipping duplicates
        List<SuggestionResponse> createdSuggestions = new ArrayList<>();

        if (parsed != null) {
            createdSuggestions = createSuggestionsFromResume(profile, parsed, file.getOriginalFilename());
        }

        upload.setStatus(UploadStatus.PROCESSED);
        resumeUploadRepository.save(upload);

        return ResumeUploadResponse.builder()
                .id(upload.getId())
                .fileName(upload.getFileName())
                .fileSizeBytes(upload.getFileSizeBytes())
                .status(upload.getStatus())
                .extractedTextLength(extractedText.length())
                .createdSuggestionsCount(createdSuggestions.size())
                .suggestions(createdSuggestions)
                .createdAt(upload.getCreatedAt() != null ? upload.getCreatedAt() : Instant.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeUploadSummaryResponse> getResumeUploadHistory(User user) {
        return resumeUploadRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(u -> ResumeUploadSummaryResponse.builder()
                        .id(u.getId())
                        .fileName(u.getFileName())
                        .fileSizeBytes(u.getFileSizeBytes())
                        .status(u.getStatus())
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void deleteResumeUpload(User user, Long resumeId) {
        ResumeUpload upload = resumeUploadRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume upload not found with id: " + resumeId));

        resumeUploadRepository.delete(upload);
        log.info("Deleted resume upload record ID: {} for user ID: {}", resumeId, user.getId());
    }

    private List<SuggestionResponse> createSuggestionsFromResume(
            Profile profile,
            ParsedResumeResult parsed,
            String sourceFileName
    ) {
        List<SuggestionResponse> suggestions = new ArrayList<>();

        // Collect existing skills to prevent duplicates
        Set<String> existingSkillNames = (profile.getProfileSkills() != null)
                ? profile.getProfileSkills().stream()
                .map(ps -> ps.getSkill() != null ? ps.getSkill().getName().toLowerCase().trim() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet())
                : new HashSet<>();

        // Collect existing project titles to prevent duplicates
        Set<String> existingProjectTitles = (profile.getProjects() != null)
                ? profile.getProjects().stream()
                .map(p -> p.getTitle() != null ? p.getTitle().toLowerCase().trim() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet())
                : new HashSet<>();

        // 1. Process Skills
        if (parsed.getSkills() != null) {
            for (ParsedSkillDto skillDto : parsed.getSkills()) {
                if (skillDto == null || skillDto.getName() == null || skillDto.getName().isBlank()) {
                    continue;
                }
                String normalizedName = skillDto.getName().toLowerCase().trim();
                if (existingSkillNames.contains(normalizedName)) {
                    log.debug("Skipping duplicate skill from resume: {}", skillDto.getName());
                    continue;
                }
                existingSkillNames.add(normalizedName);

                String proficiency = (skillDto.getProficiency() != null && !skillDto.getProficiency().isBlank())
                        ? skillDto.getProficiency().toUpperCase()
                        : "INTERMEDIATE";

                String suggestedText = skillDto.getName().trim() + " (" + proficiency + ")";
                AgentSuggestion suggestion = suggestionService.createSuggestion(
                        profile,
                        ResumeParserAgent.AGENT_NAME,
                        "SKILL",
                        null,
                        "",
                        suggestedText,
                        "Imported from resume (" + sourceFileName + ")"
                );
                suggestions.add(suggestionService.mapToResponse(suggestion));
            }
        }

        // 2. Process Projects
        if (parsed.getProjects() != null) {
            for (ParsedProjectDto projectDto : parsed.getProjects()) {
                if (projectDto == null || projectDto.getTitle() == null || projectDto.getTitle().isBlank()) {
                    continue;
                }
                String normalizedTitle = projectDto.getTitle().toLowerCase().trim();
                if (existingProjectTitles.contains(normalizedTitle)) {
                    log.debug("Skipping duplicate project from resume: {}", projectDto.getTitle());
                    continue;
                }
                existingProjectTitles.add(normalizedTitle);

                String serializedProject;
                try {
                    serializedProject = objectMapper.writeValueAsString(projectDto);
                } catch (Exception e) {
                    serializedProject = projectDto.getTitle();
                }

                AgentSuggestion suggestion = suggestionService.createSuggestion(
                        profile,
                        ResumeParserAgent.AGENT_NAME,
                        "PROJECT",
                        null,
                        "",
                        serializedProject,
                        "Imported project from resume (" + sourceFileName + ")"
                );
                suggestions.add(suggestionService.mapToResponse(suggestion));
            }
        }

        // 3. Process Headline
        if (parsed.getHeadline() != null && !parsed.getHeadline().isBlank()) {
            if (profile.getHeadline() == null || profile.getHeadline().isBlank()) {
                AgentSuggestion suggestion = suggestionService.createSuggestion(
                        profile,
                        ResumeParserAgent.AGENT_NAME,
                        "HEADLINE",
                        null,
                        "",
                        parsed.getHeadline().trim(),
                        "Extracted professional headline from resume (" + sourceFileName + ")"
                );
                suggestions.add(suggestionService.mapToResponse(suggestion));
            }
        }

        // 4. Process About
        if (parsed.getAbout() != null && !parsed.getAbout().isBlank()) {
            if (profile.getAbout() == null || profile.getAbout().isBlank()) {
                AgentSuggestion suggestion = suggestionService.createSuggestion(
                        profile,
                        ResumeParserAgent.AGENT_NAME,
                        "ABOUT",
                        null,
                        "",
                        parsed.getAbout().trim(),
                        "Extracted professional summary from resume (" + sourceFileName + ")"
                );
                suggestions.add(suggestionService.mapToResponse(suggestion));
            }
        }

        // 5. Process Education
        if (parsed.getEducation() != null) {
            Set<String> existingInstitutions = (profile.getEducation() != null)
                    ? profile.getEducation().stream()
                    .map(e -> (e.getInstitution() != null ? e.getInstitution().toLowerCase().trim() : "") + "|" +
                              (e.getDegree() != null ? e.getDegree().toLowerCase().trim() : ""))
                    .collect(Collectors.toSet())
                    : new HashSet<>();

            for (ParsedEducationDto eduDto : parsed.getEducation()) {
                if (eduDto == null || eduDto.getInstitution() == null || eduDto.getInstitution().isBlank()) {
                    continue;
                }
                String key = eduDto.getInstitution().toLowerCase().trim() + "|" +
                             (eduDto.getDegree() != null ? eduDto.getDegree().toLowerCase().trim() : "");
                if (existingInstitutions.contains(key)) {
                    log.debug("Skipping duplicate education from resume: {}", eduDto.getInstitution());
                    continue;
                }
                existingInstitutions.add(key);

                String serializedEdu;
                try {
                    serializedEdu = objectMapper.writeValueAsString(eduDto);
                } catch (Exception e) {
                    serializedEdu = eduDto.getInstitution();
                }

                AgentSuggestion suggestion = suggestionService.createSuggestion(
                        profile,
                        ResumeParserAgent.AGENT_NAME,
                        "EDUCATION",
                        null,
                        "",
                        serializedEdu,
                        "Imported education from resume (" + sourceFileName + ")"
                );
                suggestions.add(suggestionService.mapToResponse(suggestion));
            }
        }

        return suggestions;
    }
}
