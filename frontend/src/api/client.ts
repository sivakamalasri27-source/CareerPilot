import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiError } from '../types/common';

/**
 * Resolves the backend root URL (e.g., "http://localhost:8080")
 * with default http://localhost:8080, stripped of trailing slashes and /api/v1.
 */
export const getBackendBaseUrl = (): string => {
  const envUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080').trim();
  let cleanUrl = envUrl.replace(/\/+$/, '');
  while (cleanUrl.endsWith('/api/v1')) {
    cleanUrl = cleanUrl.substring(0, cleanUrl.length - 7).replace(/\/+$/, '');
  }
  return cleanUrl || 'http://localhost:8080';
};

/**
 * Resolves the frontend API base URL (e.g., "http://localhost:8080/api/v1")
 * ensuring no trailing slash and no double /api/v1.
 */
export const getApiBaseUrl = (): string => {
  const root = getBackendBaseUrl();
  return `${root}/api/v1`;
};

export const API_BASE_URL = getApiBaseUrl();

// In-memory token storage (never in localStorage)
let inMemoryAccessToken: string | null = null;
let onAuthFailureCallback: (() => void) | null = null;

export const setAccessToken = (token: string | null) => {
  inMemoryAccessToken = token;
};

export const getAccessToken = (): string | null => {
  return inMemoryAccessToken;
};

export const setAuthFailureHandler = (callback: () => void) => {
  onAuthFailureCallback = callback;
};

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
  timeout: 105000,
});

// Request interceptor to attach in-memory access token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (inMemoryAccessToken && config.headers) {
      config.headers.Authorization = `Bearer ${inMemoryAccessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Concurrency control for token refreshing
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (token) {
      prom.resolve(token);
    } else {
      prom.reject(error);
    }
  });
  failedQueue = [];
};

// Response interceptor to handle errors, 429 Retry-After, and 401 refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Format error into friendly ApiError
    const resData = error.response?.data as { message?: string; validationErrors?: Record<string, string> } | undefined;
    const validationErrors = resData?.validationErrors;
    let message = resData?.message || error.message || 'An unexpected error occurred';

    // Detect network error / unreachable server
    const isNetworkError =
      !error.response ||
      error.code === 'ERR_NETWORK' ||
      error.message === 'Network Error' ||
      error.message?.toLowerCase().includes('network error');

    if (isNetworkError) {
      const targetUrl = originalRequest?.baseURL || API_BASE_URL;
      console.error(`Cannot reach server at ${targetUrl}:`, error);
      console.error(targetUrl);
      message = `Cannot reach server. Is the backend running on ${targetUrl}?`;
    } else if (validationErrors && Object.keys(validationErrors).length > 0) {
      message = Object.entries(validationErrors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join('; ');
    }

    const apiError: ApiError = {
      status: error.response?.status || 500,
      message,
      validationErrors,
    };

    // Extract Retry-After header on 429
    if (error.response?.status === 429) {
      const retryAfter = error.response.headers['retry-after'];
      if (retryAfter) {
        apiError.retryAfterSeconds = parseInt(retryAfter, 10) || 60;
      }
    }

    // Refresh on 401 once
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      // If the 401 came from the refresh endpoint itself or auth endpoints, do not retry
      if (originalRequest.url?.includes('/auth/login') || originalRequest.url?.includes('/auth/refresh')) {
        return Promise.reject(apiError);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`;
              }
              resolve(apiClient(originalRequest));
            },
            reject: (err) => reject(err),
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const storedRefreshToken = sessionStorage.getItem('careerpilot_refresh_token') || localStorage.getItem('careerpilot_refresh_token');

      if (!storedRefreshToken) {
        isRefreshing = false;
        if (onAuthFailureCallback) onAuthFailureCallback();
        return Promise.reject(apiError);
      }

      try {
        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken: storedRefreshToken,
        }, {
          withCredentials: true,
        });

        const newAccessToken = response.data.accessToken;
        const newRefreshToken = response.data.refreshToken;

        setAccessToken(newAccessToken);
        if (newRefreshToken) {
          localStorage.setItem('careerpilot_refresh_token', newRefreshToken);
        }

        processQueue(null, newAccessToken);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }
        return apiClient(originalRequest);
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        setAccessToken(null);
        localStorage.removeItem('careerpilot_refresh_token');
        sessionStorage.removeItem('careerpilot_refresh_token');
        if (onAuthFailureCallback) {
          onAuthFailureCallback();
        }
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(apiError);
  }
);
