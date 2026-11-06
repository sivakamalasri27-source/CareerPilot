export interface ResumeUploadResponse {
  id: number;
  fileName: string;
  fileSizeBytes: number;
  status: string;
  extractedTextLength: number;
  suggestionsCreatedCount: number;
  message: string;
}

export interface ResumeUploadSummaryResponse {
  id: number;
  fileName: string;
  fileSizeBytes: number;
  status: string;
  extractedTextLength: number;
  suggestionsCreatedCount: number;
  createdAt: string;
}
