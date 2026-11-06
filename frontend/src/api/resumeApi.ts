import { apiClient } from './client';
import { ResumeUploadResponse, ResumeUploadSummaryResponse } from '../types/resume';

export const resumeApi = {
  uploadResume: async (
    file: File,
    onProgress?: (percent: number) => void
  ): Promise<ResumeUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<ResumeUploadResponse>('/resume/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percent);
        }
      },
    });
    return response.data;
  },

  getResumeHistory: async (): Promise<ResumeUploadSummaryResponse[]> => {
    const response = await apiClient.get<ResumeUploadSummaryResponse[]>('/resume');
    return response.data;
  },

  deleteResume: async (id: number): Promise<void> => {
    await apiClient.delete(`/resume/${id}`);
  },
};
