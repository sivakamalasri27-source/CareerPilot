import { apiClient } from './client';
import {
  FullAuditResponse,
  RecruiterSimulationResponse,
  VerifySkillsResponse,
  SkillGapResponse,
  AuditReportResponse,
  AuditReportSummaryResponse,
} from '../types/agent';
import { PageResponse } from '../types/common';

export const agentApi = {
  // Full Audit
  runFullAudit: async (targetRole?: string, force?: boolean): Promise<FullAuditResponse> => {
    const response = await apiClient.post<FullAuditResponse>('/agents/audit', {
      targetRole: targetRole || null,
      force: !!force,
    });
    return response.data;
  },

  // Recruiter Simulation
  runRecruiterSimulation: async (targetRole?: string): Promise<RecruiterSimulationResponse> => {
    const response = await apiClient.post<RecruiterSimulationResponse>('/agents/recruiter-simulation', {
      targetRole: targetRole || null,
    });
    return response.data;
  },

  // Experience Translator
  translateExperience: async (data: {
    projectId?: number;
    rawExperienceText?: string;
    isCoursework?: boolean;
  }): Promise<{ suggestionId: number; bullets: string[]; clarifyingQuestions: string[] }> => {
    const response = await apiClient.post('/agents/translate-experience', data);
    return response.data;
  },

  // Skill Verification (GitHub)
  verifySkills: async (): Promise<VerifySkillsResponse> => {
    const response = await apiClient.post<VerifySkillsResponse>('/agents/verify-skills');
    return response.data;
  },

  // Skill Gap Analysis
  analyzeSkillGap: async (roleId: number): Promise<SkillGapResponse> => {
    const response = await apiClient.post<SkillGapResponse>('/agents/skill-gap', { roleId });
    return response.data;
  },

  // Audit Reports History
  getReports: async (page = 0, size = 10): Promise<PageResponse<AuditReportSummaryResponse>> => {
    const response = await apiClient.get<PageResponse<AuditReportSummaryResponse>>('/reports', {
      params: { page, size },
    });
    return response.data;
  },

  getReportById: async (id: number): Promise<AuditReportResponse> => {
    const response = await apiClient.get<AuditReportResponse>(`/reports/${id}`);
    return response.data;
  },
};
