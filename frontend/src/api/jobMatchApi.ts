import { apiClient } from './client';
import { JobMatchRequest, JobMatchResponse } from '../types/jobMatch';

export const jobMatchApi = {
  matchJob: async (request: JobMatchRequest): Promise<JobMatchResponse> => {
    const response = await apiClient.post<JobMatchResponse>('/agents/job-match', request);
    return response.data;
  },

  getHistory: async (): Promise<JobMatchResponse[]> => {
    const response = await apiClient.get<JobMatchResponse[]>('/agents/job-match');
    return response.data;
  },

  getReportById: async (id: number): Promise<JobMatchResponse> => {
    const response = await apiClient.get<JobMatchResponse>(`/agents/job-match/${id}`);
    return response.data;
  },
};
