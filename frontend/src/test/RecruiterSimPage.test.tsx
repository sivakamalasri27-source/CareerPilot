import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterSimPage } from '../pages/RecruiterSimPage';
import { agentApi } from '../api/agentApi';
import { ToastProvider } from '../context/ToastContext';

vi.mock('../api/agentApi', () => ({
  agentApi: {
    getReports: vi.fn(),
    getReportById: vi.fn(),
    runRecruiterSimulation: vi.fn(),
    analyzeSkillGap: vi.fn(),
    verifySkills: vi.fn(),
  },
}));

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ToastProvider>{ui}</ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('RecruiterSimPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows empty state when recruiterFeedback is null (defensive Array.isArray guard)', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [{ id: 1, overallScore: 72, requestType: 'FULL_AUDIT', createdAt: '2026-11-01T10:00:00Z' }],
      pageNumber: 0, pageSize: 1, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    });
    vi.mocked(agentApi.getReportById).mockResolvedValue({
      id: 1,
      profileId: 10,
      overallScore: 72,
      sectionScores: {},
      weaknesses: [],
      recruiterFeedback: null, // <-- null, not an array
      requestType: 'FULL_AUDIT',
      createdAt: '2026-11-01T10:00:00Z',
    });

    renderWithProviders(<RecruiterSimPage />);

    await waitFor(() => {
      expect(screen.getByText(/no recruiter simulation run yet/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when recruiterFeedback is an empty array', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [{ id: 2, overallScore: 80, requestType: 'FULL_AUDIT', createdAt: '2026-11-01T10:00:00Z' }],
      pageNumber: 0, pageSize: 1, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    });
    vi.mocked(agentApi.getReportById).mockResolvedValue({
      id: 2,
      profileId: 10,
      overallScore: 80,
      sectionScores: {},
      weaknesses: [],
      recruiterFeedback: [], // empty array
      requestType: 'FULL_AUDIT',
      createdAt: '2026-11-01T10:00:00Z',
    });

    renderWithProviders(<RecruiterSimPage />);

    await waitFor(() => {
      expect(screen.getByText(/no recruiter simulation run yet/i)).toBeInTheDocument();
    });
  });

  it('renders persona cards when recruiterFeedback is a valid array', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [{ id: 3, overallScore: 85, requestType: 'FULL_AUDIT', createdAt: '2026-11-01T10:00:00Z' }],
      pageNumber: 0, pageSize: 1, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    });
    vi.mocked(agentApi.getReportById).mockResolvedValue({
      id: 3,
      profileId: 10,
      overallScore: 85,
      sectionScores: {},
      weaknesses: [],
      recruiterFeedback: [
        {
          persona: 'MNC HR Recruiter',
          firstImpression: 'Decent profile overall.',
          strengths: ['Spring Boot', 'REST APIs'],
          redFlags: ['No deployment experience'],
          decision: 'MAYBE',
          reasoning: 'Good potential, needs DevOps exposure.',
        },
      ],
      requestType: 'RECRUITER_SIM',
      createdAt: '2026-11-01T10:00:00Z',
    });

    renderWithProviders(<RecruiterSimPage />);

    await waitFor(() => {
      expect(screen.getByText('MNC HR Recruiter')).toBeInTheDocument();
      expect(screen.getByText(/Decent profile overall/i)).toBeInTheDocument();
      expect(screen.getByText(/DECISION: MAYBE/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when getReports returns empty content (no reports yet)', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [],
      pageNumber: 0, pageSize: 1, totalElements: 0, totalPages: 0, first: true, last: true, empty: true,
    });

    renderWithProviders(<RecruiterSimPage />);

    await waitFor(() => {
      expect(screen.getByText(/no recruiter simulation run yet/i)).toBeInTheDocument();
    });
  });
});
