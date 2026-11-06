export type TaskStatus = 'PENDING' | 'DONE';

export type VerificationType =
  | 'GITHUB_FILE_EXISTS'
  | 'GITHUB_RECENT_COMMIT'
  | 'PROFILE_CHANGE'
  | 'SELF_REPORTED';

export type VerificationStatus = 'UNVERIFIED' | 'VERIFIED' | 'SELF_REPORTED';

export interface WeeklyTaskResponse {
  id: number;
  title: string;
  description: string;
  category?: string;
  estimatedTime?: string;
  whyThisMatters?: string;
  verificationType?: VerificationType;
  verificationHint?: string | null;
  verificationStatus?: VerificationStatus;
  proofUrl?: string | null;
  status: TaskStatus;
  weekStart?: string;
  completedAt: string | null;
  createdAt?: string;
}

export interface WeeklyPlanResponse {
  weekStart: string;
  tasks: WeeklyTaskResponse[];
  isNewPlan: boolean;
  focusArea?: string;
  message?: string;
}

export interface StreakResponse {
  currentStreakWeeks?: number;
  longestStreakWeeks?: number;
  currentWeekStart?: string;
  currentWeekCompletedTasks?: number;
  currentWeekTotalTasks?: number;
  currentWeekGoalMet?: boolean;
  // Legacy / UI alias fields
  activeStreakWeeks?: number;
  completedTasksThisWeek?: number;
  totalTasksThisWeek?: number;
  currentWeekMetGoal?: boolean;
}
