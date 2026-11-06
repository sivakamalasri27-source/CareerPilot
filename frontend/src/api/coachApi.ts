import { apiClient } from './client';
import { WeeklyPlanResponse, WeeklyTaskResponse, StreakResponse } from '../types/coach';

export const coachApi = {
  getWeeklyTasks: async (weekStart?: string): Promise<WeeklyTaskResponse[]> => {
    const params: Record<string, string> = {};
    if (weekStart) {
      params.weekStart = weekStart;
    }
    const response = await apiClient.get<WeeklyTaskResponse[]>('/coach/weekly-tasks', { params });
    return response.data;
  },

  generateWeeklyPlan: async (regenerate = false): Promise<WeeklyPlanResponse> => {
    const response = await apiClient.post<WeeklyPlanResponse>('/coach/weekly-tasks/generate', null, {
      params: { regenerate },
    });
    return response.data;
  },

  completeTask: async (id: number): Promise<WeeklyTaskResponse> => {
    const response = await apiClient.patch<WeeklyTaskResponse>(`/coach/weekly-tasks/${id}/complete`);
    return response.data;
  },

  getStreak: async (): Promise<StreakResponse> => {
    const response = await apiClient.get<StreakResponse>('/coach/streak');
    return response.data;
  },
};
