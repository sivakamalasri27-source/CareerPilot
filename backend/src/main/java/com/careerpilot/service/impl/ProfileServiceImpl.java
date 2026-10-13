package com.careerpilot.service.impl;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.profile.*;
import com.careerpilot.entity.*;
import com.careerpilot.exception.DuplicateResourceException;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.mapper.EducationMapper;
import com.careerpilot.mapper.ProfileMapper;
import com.careerpilot.mapper.ProfileSkillMapper;
import com.careerpilot.mapper.ProjectMapper;
import com.careerpilot.repository.*;
import com.careerpilot.service.ProfileService;
import com.careerpilot.util.SanitizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final EducationRepository educationRepository;
    private final SkillRepository skillRepository;
    private final ProfileSkillRepository profileSkillRepository;

    private final ProfileMapper profileMapper;
    private final ProjectMapper projectMapper;
    private final EducationMapper educationMapper;
    private final ProfileSkillMapper profileSkillMapper;

    private static final int MAX_SKILLS_PER_PROFILE = 50;

    @Override
    @Transactional
    public ProfileResponse getProfile(String userEmail) {
        Profile profile = getOrCreateProfile(userEmail);
        return profileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(String userEmail, ProfileRequest request) {
        Profile profile = getOrCreateProfile(userEmail);

        // Sanitize string inputs
        request.setHeadline(SanitizationUtil.sanitize(request.getHeadline()));
        request.setAbout(SanitizationUtil.sanitize(request.getAbout()));
        request.setGithubUsername(SanitizationUtil.sanitize(request.getGithubUsername()));
        request.setTargetRole(SanitizationUtil.sanitize(request.getTargetRole()));
        request.setTargetIndustry(SanitizationUtil.sanitize(request.getTargetIndustry()));
        request.setPhone(SanitizationUtil.sanitize(request.getPhone()));
        request.setLocation(SanitizationUtil.sanitize(request.getLocation()));
        request.setLinkedinUrl(SanitizationUtil.sanitize(request.getLinkedinUrl()));
        request.setPortfolioUrl(SanitizationUtil.sanitize(request.getPortfolioUrl()));

        profileMapper.updateEntityFromDto(request, profile);
        Profile saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }

    // =========================================================================
    // Projects CRUD
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getProjects(String userEmail, Pageable pageable) {
        Profile profile = getOrCreateProfile(userEmail);
        Page<Project> projectsPage = projectRepository.findAllByProfileId(profile.getId(), pageable);
        return PageResponse.from(projectsPage.map(projectMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(String userEmail, Long projectId) {
        Profile profile = getOrCreateProfile(userEmail);
        Project project = projectRepository.findByIdAndProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponse addProject(String userEmail, ProjectRequest request) {
        Profile profile = getOrCreateProfile(userEmail);
        sanitizeProjectRequest(request);

        Project project = projectMapper.toEntity(request);
        project.setProfile(profile);

        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(String userEmail, Long projectId, ProjectRequest request) {
        Profile profile = getOrCreateProfile(userEmail);
        Project project = projectRepository.findByIdAndProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        sanitizeProjectRequest(request);
        projectMapper.updateEntityFromDto(request, project);

        Project updated = projectRepository.save(project);
        return projectMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteProject(String userEmail, Long projectId) {
        Profile profile = getOrCreateProfile(userEmail);
        Project project = projectRepository.findByIdAndProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        projectRepository.delete(project);
    }

    // =========================================================================
    // Education CRUD
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EducationResponse> getEducation(String userEmail, Pageable pageable) {
        Profile profile = getOrCreateProfile(userEmail);
        Page<Education> educationPage = educationRepository.findAllByProfileId(profile.getId(), pageable);
        return PageResponse.from(educationPage.map(educationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public EducationResponse getEducationById(String userEmail, Long educationId) {
        Profile profile = getOrCreateProfile(userEmail);
        Education education = educationRepository.findByIdAndProfileId(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Education record not found with id: " + educationId));
        return educationMapper.toResponse(education);
    }

    @Override
    @Transactional
    public EducationResponse addEducation(String userEmail, EducationRequest request) {
        Profile profile = getOrCreateProfile(userEmail);
        sanitizeEducationRequest(request);

        Education education = educationMapper.toEntity(request);
        education.setProfile(profile);

        Education saved = educationRepository.save(education);
        return educationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EducationResponse updateEducation(String userEmail, Long educationId, EducationRequest request) {
        Profile profile = getOrCreateProfile(userEmail);
        Education education = educationRepository.findByIdAndProfileId(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Education record not found with id: " + educationId));

        sanitizeEducationRequest(request);
        educationMapper.updateEntityFromDto(request, education);

        Education updated = educationRepository.save(education);
        return educationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteEducation(String userEmail, Long educationId) {
        Profile profile = getOrCreateProfile(userEmail);
        Education education = educationRepository.findByIdAndProfileId(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Education record not found with id: " + educationId));
        educationRepository.delete(education);
    }

    // =========================================================================
    // Skills CRUD
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProfileSkillResponse> getSkills(String userEmail, Pageable pageable) {
        Profile profile = getOrCreateProfile(userEmail);
        Page<ProfileSkill> skillsPage = profileSkillRepository.findAllByProfileId(profile.getId(), pageable);
        return PageResponse.from(skillsPage.map(profileSkillMapper::toResponse));
    }

    @Override
    @Transactional
    public ProfileSkillResponse addSkill(String userEmail, ProfileSkillRequest request) {
        Profile profile = getOrCreateProfile(userEmail);

        long currentSkillCount = profileSkillRepository.countByProfileId(profile.getId());
        if (currentSkillCount >= MAX_SKILLS_PER_PROFILE) {
            throw new IllegalArgumentException("Maximum limit of " + MAX_SKILLS_PER_PROFILE + " skills reached for this profile");
        }

        String rawName = SanitizationUtil.sanitize(request.getName());
        String category = SanitizationUtil.sanitize(request.getCategory());

        if (profileSkillRepository.existsByProfileIdAndSkillNameIgnoreCase(profile.getId(), rawName)) {
            throw new DuplicateResourceException("Skill already exists in your profile: " + rawName);
        }

        // Shared skills catalog lookup or creation
        Skill skill = skillRepository.findByNameIgnoreCase(rawName)
                .orElseGet(() -> skillRepository.save(Skill.builder()
                        .name(rawName)
                        .category(category)
                        .build()));

        ProfileSkill profileSkill = ProfileSkill.builder()
                .profile(profile)
                .skill(skill)
                .claimedLevel(request.getClaimedLevel())
                .verificationStatus(VerificationStatus.UNVERIFIED)
                .build();

        ProfileSkill saved = profileSkillRepository.save(profileSkill);
        return profileSkillMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileSkillResponse updateSkill(String userEmail, Long profileSkillId, ProfileSkillUpdateRequest request) {
        Profile profile = getOrCreateProfile(userEmail);
        ProfileSkill profileSkill = profileSkillRepository.findByIdAndProfileId(profileSkillId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Skill record not found with id: " + profileSkillId));

        profileSkill.setClaimedLevel(request.getClaimedLevel());
        ProfileSkill updated = profileSkillRepository.save(profileSkill);
        return profileSkillMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteSkill(String userEmail, Long profileSkillId) {
        Profile profile = getOrCreateProfile(userEmail);
        ProfileSkill profileSkill = profileSkillRepository.findByIdAndProfileId(profileSkillId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Skill record not found with id: " + profileSkillId));
        profileSkillRepository.delete(profileSkill);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Profile getOrCreateProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(Profile.builder()
                        .user(user)
                        .build()));
    }

    private void sanitizeProjectRequest(ProjectRequest request) {
        request.setTitle(SanitizationUtil.sanitize(request.getTitle()));
        request.setDescription(SanitizationUtil.sanitize(request.getDescription()));
        request.setRole(SanitizationUtil.sanitize(request.getRole()));
        request.setTechnologies(SanitizationUtil.sanitize(request.getTechnologies()));
        request.setRepoUrl(SanitizationUtil.sanitize(request.getRepoUrl()));
        request.setLiveUrl(SanitizationUtil.sanitize(request.getLiveUrl()));

        if (request.getBullets() != null) {
            List<String> sanitizedBullets = new ArrayList<>();
            for (String bullet : request.getBullets()) {
                String clean = SanitizationUtil.sanitize(bullet);
                if (clean != null && !clean.isBlank()) {
                    sanitizedBullets.add(clean);
                }
            }
            request.setBullets(sanitizedBullets);
        }
    }

    private void sanitizeEducationRequest(EducationRequest request) {
        request.setInstitution(SanitizationUtil.sanitize(request.getInstitution()));
        request.setDegree(SanitizationUtil.sanitize(request.getDegree()));
        request.setFieldOfStudy(SanitizationUtil.sanitize(request.getFieldOfStudy()));
        request.setGradeOrGpa(SanitizationUtil.sanitize(request.getGradeOrGpa()));
        request.setActivities(SanitizationUtil.sanitize(request.getActivities()));
    }
}
