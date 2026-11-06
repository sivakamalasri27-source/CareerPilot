import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuditReportPage } from '../pages/AuditReportPage';
import { agentApi } from '../api/agentApi';
import * as ToastContextModule from '../context/ToastContext';

vi.mock('../api/agentApi', () => ({
  agentApi: {
    getReports: vi.fn(),
    getReportById: vi.fn(),
    runFullAudit: vi.fn(),
  },
}));

const mockReport = {
  id: 1,
  profileId: 1,
  overallScore: 85,
  sectionScores: {
    headline: 88,
    about: 85,
    skills: 90,
    projects: 86,
    education: 80,
  },
  weaknesses: [
    {
      section: 'PROJECTS',
      weakText: 'Built simple app',
      reason: 'Lacks measurable metrics',
      improvementSuggestion: 'Add latency reduction percentages',
    },
  ],
  recruiterFeedback: [],
  requestType: 'FULL_AUDIT',
  createdAt: '2026-10-23T10:00:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastContextModule.ToastProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastContextModule.ToastProvider>
    </QueryClientProvider>
  );
};

describe('AuditReportPage Component', () => {
  const showToastMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(ToastContextModule, 'useToast').mockReturnValue({
      showToast: showToastMock,
      removeToast: vi.fn(),
    });
  });

  it('reads overall score correctly from backend response and does not display undefined in toast', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [{ id: 1, overallScore: 85, requestType: 'FULL_AUDIT', createdAt: '2026-10-23T10:00:00Z' }],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(agentApi.getReportById).mockResolvedValue(mockReport);

    // Mock backend returning auditorResult with data.overallScore
    vi.mocked(agentApi.runFullAudit).mockResolvedValue({
      reportId: 2,
      overallScore: 85,
      auditorResult: {
        status: 'SUCCESS',
        data: {
          overallScore: 85,
          sectionScores: { headline: 88, about: 85, skills: 90, projects: 86, education: 80 },
          weaknesses: [],
        },
      },
      unchanged: false,
    } as any);

    renderWithProviders(<AuditReportPage />);

    const runBtn = await screen.findByRole('button', { name: /run new audit/i });
    fireEvent.click(runBtn);

    await waitFor(() => {
      expect(agentApi.runFullAudit).toHaveBeenCalled();
      expect(showToastMock).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'success',
          title: 'Audit Complete',
          message: 'Profile score: 85/100. New report generated.',
        })
      );
      // Ensure "undefined" was not used
      expect(showToastMock).not.toHaveBeenCalledWith(
        expect.objectContaining({
          message: expect.stringContaining('undefined'),
        })
      );
    });
  });

  it('detects unchanged profile and displays "No changes since last audit" in toast and UI', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [{ id: 1, overallScore: 85, requestType: 'FULL_AUDIT', createdAt: '2026-10-23T10:00:00Z' }],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(agentApi.getReportById).mockResolvedValue(mockReport);

    vi.mocked(agentApi.runFullAudit).mockResolvedValue({
      reportId: 1,
      overallScore: 85,
      unchanged: true,
    } as any);

    renderWithProviders(<AuditReportPage />);

    const runBtn = await screen.findByRole('button', { name: /run new audit/i });
    fireEvent.click(runBtn);

    await waitFor(() => {
      expect(showToastMock).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'info',
          title: 'Profile Unchanged',
          message: 'No changes since last audit',
        })
      );
    });

    // Check UI badge/notice
    expect(await screen.findByText('No changes since last audit')).toBeInTheDocument();

    // Check Re-run anyway button
    const rerunBtn = await screen.findByRole('button', { name: /re-run anyway/i });
    expect(rerunBtn).toBeInTheDocument();
    fireEvent.click(rerunBtn);

    await waitFor(() => {
      expect(agentApi.runFullAudit).toHaveBeenCalledWith(undefined, true);
    });
  });

  it('displays "AI service unavailable: <reason>. Please retry." and Retry button on failed audit', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    });

    const errorReason = 'AI service unavailable: Gemini rejected the API key. Please retry.';
    vi.mocked(agentApi.runFullAudit).mockRejectedValueOnce({
      status: 503,
      message: errorReason,
    });

    renderWithProviders(<AuditReportPage />);

    const runBtn = await screen.findByRole('button', { name: /run full profile audit/i });
    fireEvent.click(runBtn);

    // Error banner should be visible with exact message and Retry button
    const banner = await screen.findByTestId('audit-error-banner');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent(errorReason);

    const retryBtn = screen.getByRole('button', { name: /retry/i });
    expect(retryBtn).toBeInTheDocument();

    // Ensure "Audit Complete" or "No weaknesses detected" is never shown
    expect(showToastMock).not.toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Audit Complete' })
    );

    // Clicking Retry attempts audit again
    vi.mocked(agentApi.runFullAudit).mockResolvedValueOnce({
      reportId: 5,
      overallScore: 80,
      unchanged: false,
    } as any);

    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(agentApi.runFullAudit).toHaveBeenCalledTimes(2);
    });
  });
});
