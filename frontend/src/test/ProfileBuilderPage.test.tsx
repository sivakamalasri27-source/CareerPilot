import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  ProfileBuilderPage,
  extractGithubUsername,
  isValidGithubUsername,
  countWords,
} from '../pages/ProfileBuilderPage';
import { profileApi } from '../api/profileApi';
import { ToastProvider } from '../context/ToastContext';

vi.mock('../api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
    getSkills: vi.fn(),
    addSkill: vi.fn(),
    updateSkill: vi.fn(),
    deleteSkill: vi.fn(),
    getProjects: vi.fn(),
    addProject: vi.fn(),
    updateProject: vi.fn(),
    deleteProject: vi.fn(),
    getEducation: vi.fn(),
    addEducation: vi.fn(),
    updateEducation: vi.fn(),
    deleteEducation: vi.fn(),
  },
}));

const mockProfile = {
  id: 1,
  userId: 1,
  headline: 'Software Engineer',
  about: 'Building resilient backend platforms.',
  githubUsername: 'octocat',
  targetRole: 'Backend Developer',
  targetIndustry: 'Technology',
  phone: '555-1234',
  location: 'San Francisco, CA',
  linkedinUrl: 'https://linkedin.com/in/octocat',
  portfolioUrl: 'https://octocat.dev',
  version: 1,
  createdAt: '2026-10-20T00:00:00Z',
  updatedAt: '2026-10-20T00:00:00Z',
};

const emptyPage = {
  content: [],
  pageNumber: 0,
  pageSize: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  empty: true,
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

describe('ProfileBuilderPage & GitHub Username Validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(profileApi.getProfile).mockResolvedValue(mockProfile);
    vi.mocked(profileApi.getSkills).mockResolvedValue(emptyPage as any);
    vi.mocked(profileApi.getProjects).mockResolvedValue(emptyPage as any);
    vi.mocked(profileApi.getEducation).mockResolvedValue(emptyPage as any);
  });

  describe('extractGithubUsername utility', () => {
    it('extracts username from https URL with trailing slash', () => {
      expect(extractGithubUsername('https://github.com/octocat/')).toBe('octocat');
    });

    it('extracts username from github.com without protocol', () => {
      expect(extractGithubUsername('github.com/octocat')).toBe('octocat');
    });

    it('extracts username with @ prefix', () => {
      expect(extractGithubUsername('@octocat')).toBe('octocat');
    });

    it('returns clean username unchanged', () => {
      expect(extractGithubUsername('octocat')).toBe('octocat');
    });

    it('handles empty input gracefully', () => {
      expect(extractGithubUsername('')).toBe('');
    });
  });

  describe('isValidGithubUsername utility', () => {
    it('validates standard usernames and single hyphens', () => {
      expect(isValidGithubUsername('octocat')).toBe(true);
      expect(isValidGithubUsername('octo-cat')).toBe(true);
      expect(isValidGithubUsername('dev123')).toBe(true);
    });

    it('rejects usernames starting or ending with a hyphen', () => {
      expect(isValidGithubUsername('-octocat')).toBe(false);
      expect(isValidGithubUsername('octocat-')).toBe(false);
    });

    it('rejects usernames with consecutive hyphens', () => {
      expect(isValidGithubUsername('octo--cat')).toBe(false);
    });

    it('rejects invalid characters', () => {
      expect(isValidGithubUsername('octo cat')).toBe(false);
      expect(isValidGithubUsername('octocat!')).toBe(false);
    });
  });

  describe('GitHub Username Inline Error in UI', () => {
    it('shows inline error message when invalid username is entered', async () => {
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      const input = screen.getByLabelText(/GitHub Username/i);
      expect(input).toHaveValue('octocat');

      // Change to invalid username starting with hyphen
      fireEvent.change(input, { target: { value: '-invalid-name' } });

      expect(
        screen.getByText('Enter only your GitHub username, for example octocat')
      ).toBeInTheDocument();

      // Change to valid username
      fireEvent.change(input, { target: { value: 'valid-name' } });

      expect(
        screen.queryByText('Enter only your GitHub username, for example octocat')
      ).not.toBeInTheDocument();
    });

    it('extracts username when pasting full github URL into field', async () => {
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      const input = screen.getByLabelText(/GitHub Username/i);
      fireEvent.change(input, { target: { value: 'https://github.com/new-handle/' } });

      expect(input).toHaveValue('new-handle');
      expect(
        screen.queryByText('Enter only your GitHub username, for example octocat')
      ).not.toBeInTheDocument();
    });
  });

  describe('countWords utility', () => {
    it('returns 0 for empty, null, or whitespace-only string', () => {
      expect(countWords('')).toBe(0);
      expect(countWords('   ')).toBe(0);
    });

    it('counts words accurately across multiple spaces and newlines', () => {
      expect(countWords('Software Engineer')).toBe(2);
      expect(countWords('  Java   Spring   Boot   Developer  ')).toBe(4);
      expect(countWords('Line 1\nLine 2\nLine 3')).toBe(6);
    });
  });

  describe('Real-time word and character guide under Headline and About', () => {
    it('renders real-time character and word guide under Headline with quality warning when short', async () => {
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/Professional Headline/i)).toBeInTheDocument();
      });

      const headlineInput = screen.getByLabelText(/Professional Headline/i);
      // Change to short headline (< 40 chars)
      fireEvent.change(headlineInput, { target: { value: 'Junior Dev' } });

      // Check real-time guidance message
      expect(
        screen.getByText(/30 more characters needed to reach 40-character minimum/i)
      ).toBeInTheDocument();
      // Check word and character counters
      expect(screen.getByText(/2 words/i)).toBeInTheDocument();
      expect(screen.getByText(/10\/120 chars/i)).toBeInTheDocument();

      // Change to strong headline (>= 40 chars)
      fireEvent.change(headlineInput, {
        target: { value: 'Junior Full Stack Developer | Java, Spring Boot & React' },
      });

      expect(
        screen.getByText(/Strong headline length \(min 40 characters met\)/i)
      ).toBeInTheDocument();
      expect(screen.getByText(/55\/120 chars/i)).toBeInTheDocument();
    });

    it('renders real-time character and word guide under About with quality warning when short', async () => {
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/About \/ Summary/i)).toBeInTheDocument();
      });

      const aboutInput = screen.getByLabelText(/About \/ Summary/i);
      // Change to short bio (< 100 chars)
      fireEvent.change(aboutInput, { target: { value: 'I like coding backend microservices.' } });

      expect(
        screen.getByText(/64 more characters needed to reach 100-character minimum/i)
      ).toBeInTheDocument();
      expect(screen.getByText(/5 words/i)).toBeInTheDocument();
      expect(screen.getByText(/36\/2000 chars/i)).toBeInTheDocument();

      // Change to full bio (>= 100 chars)
      fireEvent.change(aboutInput, {
        target: {
          value:
            'Computer science graduate with 2 years of hands-on project experience designing scalable Spring Boot microservices and RESTful APIs.',
        },
      });

      expect(
        screen.getByText(/Strong summary length \(min 100 characters met\)/i)
      ).toBeInTheDocument();
      expect(screen.getByText(/132\/2000 chars/i)).toBeInTheDocument();
    });
  });

  describe('Profile Completeness Quality Hints per Card', () => {
    it('shows amber "Needs improvement" and one-line tip when headline is under 40 chars', async () => {
      // mockProfile has headline "Software Engineer" (17 chars < 40)
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      expect(
        screen.getByText('Headline under 40 chars — add target role & key tech')
      ).toBeInTheDocument();
    });

    it('shows amber "Needs improvement" and one-line tip when about is under 100 chars', async () => {
      // mockProfile has about "Building resilient backend platforms." (37 chars < 100)
      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      expect(
        screen.getByText('About under 100 chars — describe passions & tech stack')
      ).toBeInTheDocument();
    });

    it('shows amber "Needs improvement" and one-line tip when skills are under 5', async () => {
      vi.mocked(profileApi.getSkills).mockResolvedValue({
        content: [
          { id: 1, skill: { id: 1, name: 'Java', category: 'Backend' }, claimedLevel: 'INTERMEDIATE', verified: true },
          { id: 2, skill: { id: 2, name: 'Spring Boot', category: 'Backend' }, claimedLevel: 'INTERMEDIATE', verified: true },
        ],
        totalElements: 2,
        pageNumber: 0,
        pageSize: 50,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      } as any);

      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      expect(
        screen.getByText('Add at least 5 skills (currently 2 added)')
      ).toBeInTheDocument();
    });

    it('shows amber "Needs improvement" and one-line tip when project has no bullets', async () => {
      vi.mocked(profileApi.getProjects).mockResolvedValue({
        content: [
          {
            id: 1,
            title: 'Simple App',
            description: 'Basic app',
            role: 'Dev',
            technologies: 'Java',
            repoUrl: 'https://github.com/octocat/app',
            liveUrl: '',
            startDate: '2025-01-01',
            endDate: '2025-06-01',
            isAcademic: false,
            bullets: [], // No bullets!
            createdAt: '2026-10-20T00:00:00Z',
            updatedAt: '2026-10-20T00:00:00Z',
          },
        ],
        totalElements: 1,
        pageNumber: 0,
        pageSize: 50,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      } as any);

      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/GitHub Username/i)).toBeInTheDocument();
      });

      expect(
        screen.getByText('Project has no bullets — add achievement bullet points')
      ).toBeInTheDocument();
    });

    it('shows 100% complete with green ticks when all quality checks pass', async () => {
      // Complete profile with > 40 chars headline, > 100 chars about
      vi.mocked(profileApi.getProfile).mockResolvedValue({
        ...mockProfile,
        headline: 'Senior Full Stack Java Engineer | Spring Boot, Microservices & React',
        about:
          'Passionate software engineer with extensive experience building scalable distributed systems, cloud microservices, and modern user experiences using Java and React.',
      });

      // 5 skills
      vi.mocked(profileApi.getSkills).mockResolvedValue({
        content: [
          { id: 1, skill: { id: 1, name: 'Java', category: 'Backend' }, claimedLevel: 'ADVANCED', verified: true },
          { id: 2, skill: { id: 2, name: 'Spring Boot', category: 'Backend' }, claimedLevel: 'ADVANCED', verified: true },
          { id: 3, skill: { id: 3, name: 'PostgreSQL', category: 'Database' }, claimedLevel: 'INTERMEDIATE', verified: true },
          { id: 4, skill: { id: 4, name: 'Docker', category: 'DevOps' }, claimedLevel: 'INTERMEDIATE', verified: true },
          { id: 5, skill: { id: 5, name: 'React', category: 'Frontend' }, claimedLevel: 'INTERMEDIATE', verified: true },
        ],
        totalElements: 5,
        pageNumber: 0,
        pageSize: 50,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      } as any);

      // 1 project with bullets
      vi.mocked(profileApi.getProjects).mockResolvedValue({
        content: [
          {
            id: 1,
            title: 'Cloud Microservices',
            description: 'Scalable backend services',
            role: 'Lead Architect',
            technologies: 'Java, Spring Boot, Docker, PostgreSQL',
            repoUrl: 'https://github.com/octocat/cloud',
            liveUrl: '',
            startDate: '2025-01-01',
            endDate: '2025-06-01',
            isAcademic: false,
            bullets: [
              'Designed event-driven architecture using Apache Kafka',
              'Achieved 99.99% service uptime with automated container deployments',
            ],
            createdAt: '2026-10-20T00:00:00Z',
            updatedAt: '2026-10-20T00:00:00Z',
          },
        ],
        totalElements: 1,
        pageNumber: 0,
        pageSize: 50,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      } as any);

      // Education
      vi.mocked(profileApi.getEducation).mockResolvedValue({
        content: [
          {
            id: 1,
            institution: 'University of Technology',
            degree: 'Bachelor of Science',
            fieldOfStudy: 'Computer Science',
            gradeOrGpa: '3.9',
            startDate: '2021-09-01',
            endDate: '2025-05-31',
            activities: '',
          },
        ],
        totalElements: 1,
        pageNumber: 0,
        pageSize: 50,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      } as any);

      renderWithProviders(<ProfileBuilderPage />);

      await waitFor(() => {
        expect(screen.getByText(/100% Complete \(7\/7\)/i)).toBeInTheDocument();
      });

      expect(
        screen.getByText('Your profile is comprehensive! Ready for AI audits, skill verification, and recruiter simulation.')
      ).toBeInTheDocument();

      // No cards should display "Needs improvement"
      expect(screen.queryByText(/Needs improvement/i)).not.toBeInTheDocument();
    });
  });
});
