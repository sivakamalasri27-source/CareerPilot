import { apiClient } from './client';
import {
  SuggestionResponse,
  SuggestionStatus,
  ApproveSuggestionRequest,
} from '../types/suggestion';
import { PageResponse } from '../types/common';

export const suggestionApi = {
  getSuggestions: async (
    status?: SuggestionStatus,
    page = 0,
    size = 20
  ): Promise<PageResponse<SuggestionResponse>> => {
    const params: Record<string, unknown> = { page, size };
    if (status) {
      params.status = status;
    }
    const response = await apiClient.get<PageResponse<SuggestionResponse>>('/suggestions', {
      params,
    });
    return response.data;
  },

  getSuggestionById: async (id: number): Promise<SuggestionResponse> => {
    const response = await apiClient.get<SuggestionResponse>(`/suggestions/${id}`);
    return response.data;
  },

  approveSuggestion: async (
    id: number,
    request?: ApproveSuggestionRequest
  ): Promise<SuggestionResponse> => {
    const response = await apiClient.patch<SuggestionResponse>(
      `/suggestions/${id}/approve`,
      request || {}
    );
    return response.data;
  },

  rejectSuggestion: async (id: number): Promise<SuggestionResponse> => {
    const response = await apiClient.patch<SuggestionResponse>(`/suggestions/${id}/reject`);
    return response.data;
  },
};
