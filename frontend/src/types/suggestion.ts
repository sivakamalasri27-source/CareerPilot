export type SuggestionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface SuggestionResponse {
  id: number;
  agentName: string;
  targetSection: string;
  targetId: number | null;
  originalText: string;
  suggestedText: string;
  suggestedBullets?: string[];
  rationale: string;
  clarifyingQuestions?: string[] | null;
  originalTextHash?: string;
  status: SuggestionStatus;
  createdAt: string;
  decidedAt: string | null;
}

export interface ApproveSuggestionRequest {
  editedText?: string;
  originalTextHash?: string;
}
