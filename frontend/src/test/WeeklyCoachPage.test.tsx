import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WeeklyCoachPage } from '../pages/WeeklyCoachPage';
import { coachApi } from '../api/coachApi';
import { ToastProvider } from '../context/ToastContext';
import { WeeklyTaskResponse, StreakResponse } from '../types/coach';

vi.mock('../api/coachApi', () => ({
  coachApi: {
    getWeeklyTasks: vi.fn(),
    getStreak: vi.fn(),
    generateWeeklyPlan: vi.fn(),
    completeTask: vi.fn(),
  },
}));

const mockTasks: WeeklyTaskResponse[] = [
  {
    id: 1,
    title: 'Containerize Spring Boot Service with Dockerfile',
    description: 'Write a multi-stage Dockerfile packaging your Spring Boot application.',
    category: 'PROJECT_IMPROVEMENT',
    estimatedTime: '~1-2 hours',
    whyThisMatters: 'Containerization proves you can package applications for production.',
    verificationType: 'GITHUB_FILE_EXISTS',
    verificationHint: 'Dockerfile',
    verificationStatus: 'VERIFIED',
    proofUrl: 'https://github.com/testuser/repo/blob/main/Dockerfile',
    status: 'DONE',
    completedAt: '2026-10-20T10:00:00Z',
    weekStart: '2026-10-19',
  },
  {
    id: 2,
    title: 'Add README with Architecture Diagram',
    description: 'Create a clear README.md with setup prerequisites and run instructions.',
    category: 'PROFILE_OPTIMIZATION',
    estimatedTime: '~1 hour',
    whyThisMatters: 'A polished README makes your repository recruiter-ready.',
    verificationType: 'PROFILE_CHANGE',
    verificationHint: 'projects',
    verificationStatus: 'SELF_REPORTED',
    proofUrl: null,
    status: 'DONE',
    completedAt: '2026-10-20T11:00:00Z',
    weekStart: '2026-10-19',
  },
  {
    id: 3,
    title: 'Write Unit Tests for One Service Class',
    description: 'Implement JUnit 5 tests covering happy path and edge cases.',
    category: 'SKILL_BUILDING',
    estimatedTime: '~1-2 hours',
    whyThisMatters: 'Demonstrates test discipline and safeguards code reliability.',
    verificationType: 'GITHUB_RECENT_COMMIT',
    verificationHint: null,
    verificationStatus: 'UNVERIFIED',
    proofUrl: null,
    status: 'PENDING',
    completedAt: null,
    weekStart: '2026-10-19',
  },
];

const mockStreak: StreakResponse = {
  currentStreakWeeks: 2,
  currentWeekCompletedTasks: 2,
  currentWeekTotalTasks: 3,
  currentWeekGoalMet: true,
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
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
};

describe('WeeklyCoachPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(coachApi.getWeeklyTasks).mockResolvedValue(mockTasks);
    vi.mocked(coachApi.getStreak).mockResolvedValue(mockStreak);
  });

  it('renders "Generate Plan" button and does not contain "Sync Plan"', async () => {
    renderWithProviders(<WeeklyCoachPage />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /generate plan/i })).toBeInTheDocument();
    });

    expect(screen.queryByText(/sync plan/i)).not.toBeInTheDocument();
  });

  it('renders "New plan every Monday" and date range instead of "Resets next Monday at 00:00 UTC"', async () => {
    renderWithProviders(<WeeklyCoachPage />);

    await waitFor(() => {
      expect(screen.getByText(/new plan every monday/i)).toBeInTheDocument();
      // Date range Oct 19 - Oct 25 should be rendered
      expect(screen.getAllByText(/oct 19 - oct 25/i).length).toBeGreaterThan(0);
      expect(screen.queryByText(/resets next monday at 00:00 utc/i)).not.toBeInTheDocument();
    });
  });

  it('shows estimated time badge and "Why this matters" line on each task', async () => {
    renderWithProviders(<WeeklyCoachPage />);

    await waitFor(() => {
      expect(screen.getByText('Add README with Architecture Diagram')).toBeInTheDocument();
    });

    // Estimated time
    expect(screen.getByText('~1 hour')).toBeInTheDocument();
    expect(screen.getAllByText('~1-2 hours').length).toBe(2);

    // Why this matters
    expect(screen.getAllByText(/why this matters:/i).length).toBe(3);
    expect(screen.getByText(/a polished readme makes your repository recruiter-ready/i)).toBeInTheDocument();
  });

  it('displays the celebration banner when 2 of 3 tasks are done', async () => {
    renderWithProviders(<WeeklyCoachPage />);

    await waitFor(() => {
      expect(screen.getByText(/weekly consistency goal achieved!/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/2 of 3 completed/i)).toBeInTheDocument();
    expect(screen.getByText(/your weekly consistency streak is officially secured!/i)).toBeInTheDocument();
  });

  it('displays Verified badge with repo proof link for verified task and Self-Reported badge for unverified task', async () => {
    renderWithProviders(<WeeklyCoachPage />);

    await waitFor(() => {
      expect(screen.getByText('Verified')).toBeInTheDocument();
    });

    // Task 1 is verified
    expect(screen.getByText(/view repo proof/i)).toBeInTheDocument();

    // Task 2 is self-reported
    expect(screen.getByText('Self-Reported')).toBeInTheDocument();
    expect(screen.getByText('Not verified')).toBeInTheDocument();

    // Task 3 is pending with verification hint
    expect(screen.getByText(/recent commit/i)).toBeInTheDocument();
  });
});
