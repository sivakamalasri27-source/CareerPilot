import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SuggestionsPage } from '../pages/SuggestionsPage';
import { suggestionApi } from '../api/suggestionApi';
import { ToastProvider } from '../context/ToastContext';
import { SuggestionResponse } from '../types/suggestion';

vi.mock('../api/suggestionApi', () => ({
  suggestionApi: {
    getSuggestions: vi.fn(),
    approveSuggestion: vi.fn(),
    rejectSuggestion: vi.fn(),
  },
}));

const mockSuggestions: SuggestionResponse[] = [
  {
    id: 101,
    agentName: 'ExperienceTranslatorAgent',
    targetSection: 'PROJECTS',
    targetId: 5,
    originalText: 'Created a simple chat app with web sockets.',
    suggestedText: 'Architected real-time chat application utilizing WebSocket protocols and Spring Boot.',
    rationale: 'Elevated technical phrasing with action verbs and specific protocol naming.',
    clarifyingQuestions: ['How many concurrent connections were supported during load testing?'],
    originalTextHash: 'abc123hash',
    status: 'PENDING',
    createdAt: '2026-10-16T10:00:00Z',
    decidedAt: null,
  },
];

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

describe('SuggestionsPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders side-by-side original text vs suggested text and agent name', async () => {
    vi.mocked(suggestionApi.getSuggestions).mockResolvedValue({
      content: mockSuggestions,
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });

    renderWithProviders(<SuggestionsPage />);

    expect(await screen.findByText('ExperienceTranslatorAgent')).toBeInTheDocument();
    expect(screen.getByText('Created a simple chat app with web sockets.')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Architected real-time chat application utilizing WebSocket protocols and Spring Boot.'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Elevated technical phrasing with action verbs/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/How many concurrent connections were supported/i)
    ).toBeInTheDocument();
  });

  it('calls approveSuggestion when Approve button is clicked', async () => {
    vi.mocked(suggestionApi.getSuggestions).mockResolvedValue({
      content: mockSuggestions,
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(suggestionApi.approveSuggestion).mockResolvedValue({
      ...mockSuggestions[0],
      status: 'APPROVED',
      decidedAt: '2026-10-16T10:05:00Z',
    });

    renderWithProviders(<SuggestionsPage />);

    const approveBtn = await screen.findByRole('button', { name: /^approve suggestion$/i });
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(suggestionApi.approveSuggestion).toHaveBeenCalledWith(101, undefined);
    });
  });

  it('handles 409 conflict error gracefully and shows refresh action', async () => {
    vi.mocked(suggestionApi.getSuggestions).mockResolvedValue({
      content: mockSuggestions,
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });

    const error409 = {
      status: 409,
      message: 'Target project has been updated since suggestion creation. Text is stale.',
    };
    vi.mocked(suggestionApi.approveSuggestion).mockRejectedValue(error409);

    renderWithProviders(<SuggestionsPage />);

    const approveBtn = await screen.findByRole('button', { name: /^approve suggestion$/i });
    fireEvent.click(approveBtn);

    expect(
      await screen.findByText(/Stale or Conflicted Suggestion \(409\)/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Target project has been updated/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /refresh suggestions/i })).toBeInTheDocument();
  });
});
