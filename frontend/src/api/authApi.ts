import { apiClient, setAccessToken } from './client';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../types/auth';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', credentials);
    setAccessToken(response.data.accessToken);
    if (response.data.refreshToken) {
      localStorage.setItem('careerpilot_refresh_token', response.data.refreshToken);
    }
    return response.data;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', data);
    setAccessToken(response.data.accessToken);
    if (response.data.refreshToken) {
      localStorage.setItem('careerpilot_refresh_token', response.data.refreshToken);
    }
    return response.data;
  },

  refresh: async (refreshToken: string): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/refresh', { refreshToken });
    setAccessToken(response.data.accessToken);
    if (response.data.refreshToken) {
      localStorage.setItem('careerpilot_refresh_token', response.data.refreshToken);
    }
    return response.data;
  },

  logout: async (): Promise<void> => {
    const refreshToken = localStorage.getItem('careerpilot_refresh_token');
    try {
      if (refreshToken) {
        await apiClient.post('/auth/logout', { refreshToken });
      }
    } finally {
      setAccessToken(null);
      localStorage.removeItem('careerpilot_refresh_token');
      sessionStorage.removeItem('careerpilot_refresh_token');
    }
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<User>('/auth/me');
    return response.data;
  },
};
