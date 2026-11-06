import { apiClient } from './client';
import {
  ProfileResponse,
  ProfileRequest,
  ProfileSkillResponse,
  ProfileSkillRequest,
  ProfileSkillUpdateRequest,
  ProjectResponse,
  ProjectRequest,
  EducationResponse,
  EducationRequest,
} from '../types/profile';
import { PageResponse } from '../types/common';

export const profileApi = {
  // Profile
  getProfile: async (): Promise<ProfileResponse> => {
    const response = await apiClient.get<ProfileResponse>('/profile');
    return response.data;
  },

  updateProfile: async (data: ProfileRequest): Promise<ProfileResponse> => {
    const response = await apiClient.put<ProfileResponse>('/profile', data);
    return response.data;
  },

  // Skills
  getSkills: async (page = 0, size = 50): Promise<PageResponse<ProfileSkillResponse>> => {
    const response = await apiClient.get<PageResponse<ProfileSkillResponse>>('/profile/skills', {
      params: { page, size, sort: 'createdAt,desc' },
    });
    return response.data;
  },

  addSkill: async (data: ProfileSkillRequest): Promise<ProfileSkillResponse> => {
    const response = await apiClient.post<ProfileSkillResponse>('/profile/skills', data);
    return response.data;
  },

  updateSkill: async (id: number, data: ProfileSkillUpdateRequest): Promise<ProfileSkillResponse> => {
    const response = await apiClient.put<ProfileSkillResponse>(`/profile/skills/${id}`, data);
    return response.data;
  },

  deleteSkill: async (id: number): Promise<void> => {
    await apiClient.delete(`/profile/skills/${id}`);
  },

  // Projects
  getProjects: async (page = 0, size = 20): Promise<PageResponse<ProjectResponse>> => {
    const response = await apiClient.get<PageResponse<ProjectResponse>>('/profile/projects', {
      params: { page, size, sort: 'startDate,desc' },
    });
    return response.data;
  },

  getProject: async (id: number): Promise<ProjectResponse> => {
    const response = await apiClient.get<ProjectResponse>(`/profile/projects/${id}`);
    return response.data;
  },

  addProject: async (data: ProjectRequest): Promise<ProjectResponse> => {
    const response = await apiClient.post<ProjectResponse>('/profile/projects', data);
    return response.data;
  },

  updateProject: async (id: number, data: ProjectRequest): Promise<ProjectResponse> => {
    const response = await apiClient.put<ProjectResponse>(`/profile/projects/${id}`, data);
    return response.data;
  },

  deleteProject: async (id: number): Promise<void> => {
    await apiClient.delete(`/profile/projects/${id}`);
  },

  // Education
  getEducation: async (page = 0, size = 20): Promise<PageResponse<EducationResponse>> => {
    const response = await apiClient.get<PageResponse<EducationResponse>>('/profile/education', {
      params: { page, size, sort: 'startDate,desc' },
    });
    return response.data;
  },

  addEducation: async (data: EducationRequest): Promise<EducationResponse> => {
    const response = await apiClient.post<EducationResponse>('/profile/education', data);
    return response.data;
  },

  updateEducation: async (id: number, data: EducationRequest): Promise<EducationResponse> => {
    const response = await apiClient.put<EducationResponse>(`/profile/education/${id}`, data);
    return response.data;
  },

  deleteEducation: async (id: number): Promise<void> => {
    await apiClient.delete(`/profile/education/${id}`);
  },
};
