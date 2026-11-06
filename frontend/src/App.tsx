import React, { useState, useEffect, useCallback } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import axios from 'axios';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { Layout } from './components/common/Layout';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { getBackendBaseUrl } from './api/client';

// Pages
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProfileBuilderPage } from './pages/ProfileBuilderPage';
import { AuditReportPage } from './pages/AuditReportPage';
import { RecruiterSimPage } from './pages/RecruiterSimPage';
import { SuggestionsPage } from './pages/SuggestionsPage';
import { SkillVerifyPage } from './pages/SkillVerifyPage';
import { SkillGapPage } from './pages/SkillGapPage';
import { WeeklyCoachPage } from './pages/WeeklyCoachPage';
import { ResumeUploadPage } from './pages/ResumeUploadPage';
import { JobMatchPage } from './pages/JobMatchPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error: any) => {
        // Do not retry 4xx errors
        if (error?.status >= 400 && error?.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});

export const App: React.FC = () => {
  const [healthError, setHealthError] = useState<string | null>(null);

  const checkHealth = useCallback(async () => {
    const rootUrl = getBackendBaseUrl();
    try {
      const response = await axios.get(`${rootUrl}/actuator/health`, {
        timeout: 5000,
        withCredentials: true,
      });
      if (response.status >= 200 && response.status < 300) {
        setHealthError(null);
      } else {
        setHealthError(`Cannot reach server. Is the backend running on ${rootUrl}?`);
      }
    } catch (error) {
      console.error(`Health check failed for ${rootUrl}/actuator/health:`, error);
      console.error(rootUrl);
      setHealthError(`Cannot reach server. Is the backend running on ${rootUrl}?`);
    }
  }, []);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          {healthError && (
            <div
              role="alert"
              className="bg-red-600 text-white px-4 py-2.5 text-center text-sm font-medium flex items-center justify-center gap-2 shadow-md sticky top-0 z-50"
            >
              <span>⚠️ {healthError}</span>
              <button
                type="button"
                onClick={checkHealth}
                className="ml-2 px-2.5 py-0.5 bg-red-700 hover:bg-red-800 rounded text-xs font-semibold text-white underline transition"
              >
                Retry
              </button>
            </div>
          )}
          <BrowserRouter>
            <Routes>
              {/* Public Routes */}
              <Route path="/" element={<LandingPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              {/* Protected Routes */}
              <Route element={<ProtectedRoute />}>
                <Route element={<Layout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/profile" element={<ProfileBuilderPage />} />
                  <Route path="/audit" element={<AuditReportPage />} />
                  <Route path="/recruiter-simulation" element={<RecruiterSimPage />} />
                  <Route path="/suggestions" element={<SuggestionsPage />} />
                  <Route path="/skills-verify" element={<SkillVerifyPage />} />
                  <Route path="/skill-gap" element={<SkillGapPage />} />
                  <Route path="/coach" element={<WeeklyCoachPage />} />
                  <Route path="/resume" element={<ResumeUploadPage />} />
                  <Route path="/job-match" element={<JobMatchPage />} />
                </Route>
              </Route>

              {/* Fallback */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
