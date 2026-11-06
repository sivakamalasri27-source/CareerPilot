export interface JobMatchedSkill {
  skillName: string;
  claimedLevel: string;
  verificationStatus: 'VERIFIED' | 'PARTIAL' | 'UNVERIFIED' | string;
}

export interface JobMissingSkill {
  skillName: string;
  category?: string;
}

export interface JobMatchRequest {
  jobDescription: string;
  jobTitle?: string;
  companyName?: string;
}

export interface JobMatchResponse {
  id: number;
  jobTitle?: string;
  companyName?: string;
  extractedExperience?: string;
  matchPercentage: number;
  matchedSkills: JobMatchedSkill[];
  missingSkills: JobMissingSkill[];
  fitSummary: string;
  profileTweaks: string[];
  interviewTopics: string[];
  createdAt: string;
}
