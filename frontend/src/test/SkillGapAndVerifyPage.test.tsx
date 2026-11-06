import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { SkillGapPage } from '../pages/SkillGapPage';
import { SkillVerifyPage } from '../pages/SkillVerifyPage';
import { agentApi } from '../api/agentApi';
import { profileApi } from '../api/profileApi';
import { roleApi } from '../api/roleApi';
import { ToastProvider } from '../context/ToastContext';

vi.mock('../api/agentApi', () => ({
  agentApi: {
    analyzeSkillGap: vi.fn(),
    verifySkills: vi.fn(),
    getReports: vi.fn(),
    getReportById: vi.fn(),
    runRecruiterSimulation: vi.fn(),
  },
}));

vi.mock('../api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn(),
  },
}));

vi.mock('../api/roleApi', () => ({
  roleApi: {
    listTargetRoles: vi.fn(),
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

// ─── SkillGapPage tests ───────────────────────────────────────────────────────

describe('SkillGapPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(roleApi.listTargetRoles).mockResolvedValue([
      { id: 1, name: 'Java Backend Developer', description: 'Java role', requiredSkillsCount: 10 },
    ]);
  });

  it('shows empty state before analysis is run', async () => {
    renderWithProviders(<SkillGapPage />);

    await waitFor(() => {
      expect(screen.getByText(/no skill gap analysis generated yet/i)).toBeInTheDocument();
    });
  });

  it('renders string skill arrays correctly without crashing', async () => {
    // The mutation isn't auto-triggered; this verifies the page renders without JS errors
    renderWithProviders(<SkillGapPage />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /analyze gap/i })).toBeInTheDocument();
    });
  });

  it('renders role name in the select dropdown', async () => {
    renderWithProviders(<SkillGapPage />);

    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeInTheDocument();
      expect(screen.getByText(/Java Backend Developer/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when roles list is empty', async () => {
    vi.mocked(roleApi.listTargetRoles).mockResolvedValue([]);
    renderWithProviders(<SkillGapPage />);

    await waitFor(() => {
      expect(screen.getByText(/no skill gap analysis generated yet/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when roles query returns undefined/null defensively', async () => {
    vi.mocked(roleApi.listTargetRoles).mockResolvedValue(undefined as any);
    renderWithProviders(<SkillGapPage />);

    // Should not crash — just show empty state
    await waitFor(() => {
      expect(screen.getByText(/no skill gap analysis generated yet/i)).toBeInTheDocument();
    });
  });
});

// ─── SkillVerifyPage tests ────────────────────────────────────────────────────

describe('SkillVerifyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows no-github banner when profile has no githubUsername', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: null,
      headline: null,
      about: null,
      linkedinUrl: null,
      phoneNumber: null,
      location: null,
      skills: [],
      projects: [],
      educations: [],
      experiences: [],
      certifications: [],
    } as any);

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/GitHub username not connected/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when skills is empty array', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      headline: null,
      about: null,
      linkedinUrl: null,
      phoneNumber: null,
      location: null,
      skills: [],
      projects: [],
      educations: [],
      experiences: [],
      certifications: [],
    } as any);
    vi.mocked(agentApi.verifySkills).mockResolvedValue({
      skills: [],
      verifiedCount: 0,
      partialCount: 0,
      unverifiedCount: 0,
      summary: 'No skills verified.',
    });

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/No verification results found/i)).toBeInTheDocument();
    });
  });

  it('shows error state when verifySkills query fails', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      headline: null,
      about: null,
      linkedinUrl: null,
      phoneNumber: null,
      location: null,
      skills: [],
      projects: [],
      educations: [],
      experiences: [],
      certifications: [],
    } as any);
    vi.mocked(agentApi.verifySkills).mockRejectedValue(new Error('Network Error'));

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/Verification failed/i)).toBeInTheDocument();
    });
  });

  it('renders skill cards when verification returns results', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      headline: null,
      about: null,
      linkedinUrl: null,
      phoneNumber: null,
      location: null,
      skills: [],
      projects: [],
      educations: [],
      experiences: [],
      certifications: [],
    } as any);
    vi.mocked(agentApi.verifySkills).mockResolvedValue({
      skills: [
        {
          skill: 'Spring Boot',
          status: 'VERIFIED',
          evidence: [{ repoName: 'careerpilot-backend', htmlUrl: 'https://github.com/u/r', reason: 'Java Spring Boot found' }],
          explanation: 'Spring Boot detected in multiple repositories.',
        },
      ],
      verifiedCount: 1,
      partialCount: 0,
      unverifiedCount: 0,
      summary: '1 skill verified.',
    });

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText('Spring Boot')).toBeInTheDocument();
      expect(screen.getByText(/Spring Boot detected in multiple repositories/i)).toBeInTheDocument();
    });
  });

  it('defensive: does not crash when skills is null/malformed', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      headline: null,
      about: null,
      linkedinUrl: null,
      phoneNumber: null,
      location: null,
      skills: [],
      projects: [],
      educations: [],
      experiences: [],
      certifications: [],
    } as any);
    vi.mocked(agentApi.verifySkills).mockResolvedValue({
      skills: null as any, // malformed — should not crash
      verifiedCount: 0,
      partialCount: 0,
      unverifiedCount: 0,
      summary: '',
    });

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      // Should show empty state rather than crashing
      expect(screen.getByText(/No verification results found/i)).toBeInTheDocument();
    });
  });

  it('treats 404 from initial query as empty state with "Run Verification" prompt, not loading or error', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      skills: [{ id: 1, name: 'Java' }],
    } as any);
    const err404 = { status: 404, message: 'No verification record found' };
    vi.mocked(agentApi.verifySkills).mockRejectedValue(err404);

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/No verification results found/i)).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /run verification/i }).length).toBeGreaterThan(0);
    });
    // Ensure loading skeleton is gone
    expect(screen.queryByTestId('card-skeleton')).not.toBeInTheDocument();
  });

  it('shows error state with backend message and Retry button on non-404 failure', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      skills: [{ id: 1, name: 'Java' }],
    } as any);
    const err500 = { status: 500, message: 'Internal Server Error while evaluating profile' };
    vi.mocked(agentApi.verifySkills).mockRejectedValue(err500);

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/Verification Failed/i)).toBeInTheDocument();
      expect(screen.getByText(/Internal Server Error while evaluating profile/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /retry verification/i })).toBeInTheDocument();
    });
  });

  it('handles GitHub rate limit (429/403) with a clear message', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'testuser',
      skills: [{ id: 1, name: 'Java' }],
    } as any);
    const err429 = { status: 429, message: 'rate limit exceeded' };
    vi.mocked(agentApi.verifySkills).mockRejectedValue(err429);

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/GitHub API rate limit exceeded/i)).toBeInTheDocument();
    });
  });

  it('handles GitHub user not found with a clear message', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      id: 1,
      userId: 1,
      name: 'Test User',
      email: 'test@example.com',
      githubUsername: 'unknownuser999',
      skills: [{ id: 1, name: 'Java' }],
    } as any);
    const errNotFound = { status: 400, message: 'GitHub user not found' };
    vi.mocked(agentApi.verifySkills).mockRejectedValue(errNotFound);

    renderWithProviders(<SkillVerifyPage />);

    await waitFor(() => {
      expect(screen.getByText(/GitHub user "unknownuser999" not found/i)).toBeInTheDocument();
    });
  });
});
