package com.careerpilot.service;

import com.careerpilot.dto.common.PageResponse;
import com.careerpilot.dto.profile.*;
import org.springframework.data.domain.Pageable;

public interface ProfileService {

    ProfileResponse getProfile(String userEmail);

    ProfileResponse updateProfile(String userEmail, ProfileRequest request);

    PageResponse<ProjectResponse> getProjects(String userEmail, Pageable pageable);

    ProjectResponse getProject(String userEmail, Long projectId);

    ProjectResponse addProject(String userEmail, ProjectRequest request);

    ProjectResponse updateProject(String userEmail, Long projectId, ProjectRequest request);

    void deleteProject(String userEmail, Long projectId);

    PageResponse<EducationResponse> getEducation(String userEmail, Pageable pageable);

    EducationResponse getEducationById(String userEmail, Long educationId);

    EducationResponse addEducation(String userEmail, EducationRequest request);

    EducationResponse updateEducation(String userEmail, Long educationId, EducationRequest request);

    void deleteEducation(String userEmail, Long educationId);

    PageResponse<ProfileSkillResponse> getSkills(String userEmail, Pageable pageable);

    ProfileSkillResponse addSkill(String userEmail, ProfileSkillRequest request);

    ProfileSkillResponse updateSkill(String userEmail, Long profileSkillId, ProfileSkillUpdateRequest request);

    void deleteSkill(String userEmail, Long profileSkillId);
}
