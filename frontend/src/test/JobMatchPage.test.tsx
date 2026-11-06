import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { JobMatchPage } from '../pages/JobMatchPage';
import { jobMatchApi } from '../api/jobMatchApi';
import { ToastProvider } from '../context/ToastContext';
import { JobMatchResponse } from '../types/jobMatch';

vi.mock('../api/jobMatchApi', () => ({
  jobMatchApi: {
    matchJob: vi.fn(),
    getHistory: vi.fn(),
    getReportById: vi.fn(),
  },
}));

const mockReport: JobMatchResponse = {
  id: 101,
  jobTitle: 'Backend Java Engineer',
  companyName: 'FinTech Innovations',
  extractedExperience: '3+ years',
  matchPercentage: 75.0,
  matchedSkills: [
    {
      skillName: 'Java',
      claimedLevel: 'ADVANCED',
      verificationStatus: 'VERIFIED',
    },
    {
      skillName: 'Spring Boot',
      claimedLevel: 'INTERMEDIATE',
      verificationStatus: 'UNVERIFIED',
    },
  ],
  missingSkills: [
    {
      skillName: 'Docker',
      category: 'DevOps',
    },
    {
      skillName: 'PostgreSQL',
      category: 'Database',
    },
  ],
  fitSummary: 'Solid foundation in Java backend architectures. Adding containerization will complete your qualification.',
  profileTweaks: [
    'Feature your Spring Boot REST API project prominently in your headline.',
    'Detail quantifiable latency improvements in your project bullet points.',
    'Highlight your familiarity with Docker containerization coursework.'
  ],
  interviewTopics: [
    'Spring Boot dependency injection and bean scope',
    'PostgreSQL indexing and query optimization strategies',
    'RESTful API idempotency and HTTP status codes'
  ],
  createdAt: '2026-10-23T10:00:00Z',
};

describe('JobMatchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (jobMatchApi.getHistory as any).mockResolvedValue([mockReport]);
  });

  const renderComponent = () => {
    return render(
      <ToastProvider>
        <JobMatchPage />
      </ToastProvider>
    );
  };

  it('renders Job Match Analyzer page with character counter and inputs', async () => {
    renderComponent();

    expect(screen.getByText('Job Match Analyzer')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Backend Engineer/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Acme Corp/i)).toBeInTheDocument();
    expect(screen.getByText(/0 \/ 6,000 chars/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Previous Job Matches (1)')).toBeInTheDocument();
    });
  });

  it('loads sample JD when clicking "Load Sample JD"', async () => {
    renderComponent();

    const loadSampleBtn = screen.getByText('Load Sample JD');
    fireEvent.click(loadSampleBtn);

    const textarea = screen.getByPlaceholderText(/Paste the full job description here/i) as HTMLTextAreaElement;
    expect(textarea.value).toContain('Backend Java Engineer');
    expect(textarea.value).toContain('Spring Boot');
    expect(screen.getByText(/chars/i)).toBeInTheDocument();
  });

  it('displays validation error when submitting empty job description', async () => {
    renderComponent();

    const submitBtn = screen.getByRole('button', { name: /Analyze Job Match/i });
    fireEvent.click(submitBtn);

    expect(screen.getByText('Please paste a job description to analyze.')).toBeInTheDocument();
    expect(jobMatchApi.matchJob).not.toHaveBeenCalled();
  });

  it('successfully analyzes job match and renders report details', async () => {
    (jobMatchApi.matchJob as any).mockResolvedValue(mockReport);

    renderComponent();

    const textarea = screen.getByPlaceholderText(/Paste the full job description here/i);
    fireEvent.change(textarea, { target: { value: 'We are seeking a Backend Java Engineer with 3+ years experience.' } });

    const submitBtn = screen.getByRole('button', { name: /Analyze Job Match/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(jobMatchApi.matchJob).toHaveBeenCalledWith({
        jobDescription: 'We are seeking a Backend Java Engineer with 3+ years experience.',
        jobTitle: undefined,
        companyName: undefined,
      });
    });

    await waitFor(() => {
      expect(screen.getByText('75.0%')).toBeInTheDocument();
      expect(screen.getByText(/Solid foundation in Java backend architectures/i)).toBeInTheDocument();
      expect(screen.getByText('3 Targeted Profile Tweaks for THIS Job')).toBeInTheDocument();
      expect(screen.getByText(/Feature your Spring Boot REST API project prominently/i)).toBeInTheDocument();
      expect(screen.getByText('Matched Skills (2)')).toBeInTheDocument();
      expect(screen.getByText('Missing Skills (2)')).toBeInTheDocument();
      expect(screen.getByText('Interview Topics to Prepare')).toBeInTheDocument();
    });
  });

  it('displays history list and allows selecting a past report', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getAllByText('FinTech Innovations').length).toBeGreaterThan(0);
    });

    expect(screen.getAllByText(/75/).length).toBeGreaterThan(0);
  });
});
