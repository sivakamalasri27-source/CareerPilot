import { VerificationStatus } from './profile';

export interface WeaknessDto {
  section: string;
  weakText: string;
  reason: string;
}

export type RecruiterDecision = 'YES' | 'MAYBE' | 'NO';

export interface RecruiterPersonaDto {
  persona: string;
  firstImpression: string;
  strengths: string[];
  redFlags: string[];
  decision: RecruiterDecision;
  reasoning: string;
}

export interface FullAuditResponse {
  reportId?: number;
  auditId?: number;
  overallScore?: number;
  sectionScores?: Record<string, number>;
  weaknesses?: WeaknessDto[];
  recruiterFeedback?: RecruiterPersonaDto[];
  status?: string;
  requestType?: string;
  auditorResult?: {
    status: string;
    data?: {
      overallScore?: number;
      sectionScores?: Record<string, number>;
      weaknesses?: WeaknessDto[];
      strengths?: string[];
      keyRecommendations?: string[];
    };
    errorMessage?: string | null;
  };
  recruiterResult?: any;
  createdAt?: string;
  unchanged?: boolean;
}

export interface RecruiterSimulationResponse {
  personas: RecruiterPersonaDto[];
  overallDecision: string;
  summary: string;
}

export interface AuditReportSummaryResponse {
  id: number;
  overallScore: number;
  requestType: string;
  createdAt: string;
}

export interface AuditReportResponse {
  id: number;
  profileId: number;
  overallScore: number;
  sectionScores: Record<string, number>;
  weaknesses: WeaknessDto[];
  recruiterFeedback: RecruiterPersonaDto[] | null;
  requestType: string;
  createdAt: string;
}

export interface SkillEvidenceDto {
  repoName: string;
  htmlUrl: string;
  reason: string;
}

export interface VerifiedSkillDto {
  skill: string;
  status: VerificationStatus;
  evidence: SkillEvidenceDto[];
  explanation: string;
}

export interface VerifySkillsResponse {
  skills: VerifiedSkillDto[];
  verifiedCount: number;
  partialCount: number;
  unverifiedCount: number;
  summary: string;
}

export interface RoadmapItemDto {
  skillName: string;
  priority: string;
  whyItMatters: string;
  whatToLearn: string;
  projectIdea: string;
}

export interface SkillGapResponse {
  roleId: number;
  roleTitle: string;
  matchPercentage: number;
  matchedSkills: string[];
  missingSkills: string[];
  weakSkills: string[];
  roadmap: RoadmapItemDto[];
}
