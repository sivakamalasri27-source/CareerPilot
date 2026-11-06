import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from '../pages/DashboardPage';
import { agentApi } from '../api/agentApi';
import { coachApi } from '../api/coachApi';
import { ToastProvider } from '../context/ToastContext';

vi.mock('../api/agentApi', () => ({
  agentApi: {
    getReports: vi.fn(),
    getReport: vi.fn(),
    runFullAudit: vi.fn(),
  },
}));

vi.mock('../api/coachApi', () => ({
  coachApi: {
    getWeeklyTasks: vi.fn(),
    getStreak: vi.fn(),
    completeTask: vi.fn(),
  },
}));

// Mock recharts ResponsiveContainer to render children cleanly in jsdom
vi.mock('recharts', async () => {
  const actual = await vi.importActual('recharts');
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container" style={{ width: 400, height: 300 }}>
        {children}
      </div>
    ),
  };
});

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
};

describe('DashboardPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty state with "Run your first audit" button when no audit exists', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [],
      pageNumber: 0,
      pageSize: 1,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    });
    vi.mocked(coachApi.getWeeklyTasks).mockResolvedValue([]);
    vi.mocked(coachApi.getStreak).mockResolvedValue({
      currentStreakWeeks: 0,
      longestStreakWeeks: 0,
      currentWeekCompletedTasks: 0,
      currentWeekTotalTasks: 3,
      currentWeekGoalMet: false,
    });
    vi.mocked(agentApi.runFullAudit).mockResolvedValue({
      reportId: 1,
      overallScore: 82,
      createdAt: '2026-10-22T10:00:00Z',
    } as any);

    renderWithProviders(<DashboardPage />);

    // Check for empty radar text
    expect(await screen.findByText('No audit run yet')).toBeInTheDocument();

    // Check for "Run your first audit" button
    const runAuditBtn = screen.getByRole('button', { name: /run your first audit/i });
    expect(runAuditBtn).toBeInTheDocument();

    // Click button and verify mutation is triggered
    fireEvent.click(runAuditBtn);
    await waitFor(() => {
      expect(agentApi.runFullAudit).toHaveBeenCalled();
    });
  });

  it('displays synced tasks completed and consistency streak counts accurately', async () => {
    vi.mocked(agentApi.getReports).mockResolvedValue({
      content: [],
      pageNumber: 0,
      pageSize: 1,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    });
    vi.mocked(coachApi.getWeeklyTasks).mockResolvedValue([
      {
        id: 1,
        title: 'Containerize App',
        description: 'Multi-stage Dockerfile',
        status: 'DONE',
        completedAt: '2026-10-22T10:00:00Z',
      },
      {
        id: 2,
        title: 'Write Unit Tests',
        description: 'Test one service class',
        status: 'PENDING',
        completedAt: null,
      },
      {
        id: 3,
        title: 'Refactor Repository Layer',
        description: 'Extract query methods',
        status: 'PENDING',
        completedAt: null,
      },
    ]);
    vi.mocked(coachApi.getStreak).mockResolvedValue({
      currentStreakWeeks: 2,
      activeStreakWeeks: 2,
      longestStreakWeeks: 3,
      currentWeekCompletedTasks: 1,
      completedTasksThisWeek: 1,
      currentWeekTotalTasks: 3,
      currentWeekGoalMet: false,
    });

    renderWithProviders(<DashboardPage />);

    // Consistency streak counter
    expect(await screen.findByText('2')).toBeInTheDocument();
    expect(screen.getByText('consecutive weeks')).toBeInTheDocument();

    // Streak card displays 1/3 tasks done
    expect(screen.getByText(/This Week:\s*1\/3 tasks done/i)).toBeInTheDocument();

    // Action plan footer displays 1 / 3 Completed
    expect(screen.getByText(/1 \/ 3 Completed/i)).toBeInTheDocument();
  });
});
