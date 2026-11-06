export type SkillProficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type VerificationStatus = 'UNVERIFIED' | 'PARTIAL' | 'VERIFIED';

export interface ProfileResponse {
  id: number;
  userId: number;
  headline: string | null;
  about: string | null;
  githubUsername: string | null;
  targetRole: string | null;
  targetIndustry: string | null;
  phone: string | null;
  location: string | null;
  linkedinUrl: string | null;
  portfolioUrl: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileRequest {
  headline: string;
  about?: string;
  githubUsername?: string;
  targetRole?: string;
  targetIndustry?: string;
  phone?: string;
  location?: string;
  linkedinUrl?: string;
  portfolioUrl?: string;
  version?: number;
}

export interface ProfileSkillResponse {
  id: number;
  skillId: number;
  name: string;
  category: string | null;
  claimedLevel: SkillProficiency;
  verificationStatus: VerificationStatus;
  evidenceSummary: string | null;
  evidenceUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileSkillRequest {
  name: string;
  claimedLevel: SkillProficiency;
  category?: string;
}

export interface ProfileSkillUpdateRequest {
  claimedLevel: SkillProficiency;
}

export interface ProjectResponse {
  id: number;
  title: string;
  description: string | null;
  role: string | null;
  technologies: string | null;
  repoUrl: string | null;
  liveUrl: string | null;
  startDate: string | null;
  endDate: string | null;
  isAcademic: boolean;
  bullets: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  title: string;
  description?: string;
  role?: string;
  technologies?: string;
  repoUrl?: string;
  liveUrl?: string;
  startDate?: string;
  endDate?: string;
  isAcademic?: boolean;
  bullets?: string[];
}

export interface EducationResponse {
  id: number;
  institution: string;
  degree: string;
  fieldOfStudy: string | null;
  startDate: string | null;
  endDate: string | null;
  gradeOrGpa: string | null;
  activities: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EducationRequest {
  institution: string;
  degree: string;
  fieldOfStudy?: string;
  startDate?: string;
  endDate?: string;
  gradeOrGpa?: string;
  activities?: string;
}
