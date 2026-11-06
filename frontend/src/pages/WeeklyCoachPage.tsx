import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { coachApi } from '../api/coachApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import { Confetti } from '../components/common/Confetti';
import {
  CalendarCheck2,
  Flame,
  CheckCircle2,
  RotateCcw,
  Sparkles,
  Trophy,
  Loader2,
  Calendar,
  Clock,
  Lightbulb,
  PartyPopper,
  ShieldCheck,
  ExternalLink,
  GitBranch,
  GitCommit,
  UserCheck,
} from 'lucide-react';
import { WeeklyTaskResponse } from '../types/coach';

export const WeeklyCoachPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showConfetti, setShowConfetti] = useState(false);

  // Queries
  const { data: weeklyTasks, isLoading: isTasksLoading } = useQuery({
    queryKey: ['weekly-tasks'],
    queryFn: () => coachApi.getWeeklyTasks(),
  });

  const { data: streak } = useQuery({
    queryKey: ['coach-streak'],
    queryFn: () => coachApi.getStreak(),
  });

  // Generate / Regenerate Plan Mutation
  const generatePlanMutation = useMutation({
    mutationFn: (regenerate: boolean) => coachApi.generateWeeklyPlan(regenerate),
    onSuccess: (data) => {
      queryClient.setQueryData(['weekly-tasks'], data.tasks);
      queryClient.invalidateQueries({ queryKey: ['coach-streak'] });
      showToast({
        type: 'success',
        title: data.isNewPlan ? 'New Plan Generated' : 'Weekly Plan Loaded',
        message: data.isNewPlan
          ? 'Generated 3 fresh actionable tasks for this week.'
          : 'Retrieved your active weekly coaching plan.',
      });
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Plan Generation Failed',
        message: err.message || 'Could not generate weekly plan.',
      });
    },
  });

  const tasks = weeklyTasks || [];
  const completedCount = tasks.filter((t) => t.status === 'DONE').length;

  // Complete Task Mutation
  const completeTaskMutation = useMutation({
    mutationFn: (id: number) => coachApi.completeTask(id),
    onSuccess: (updatedTask: WeeklyTaskResponse) => {
      setShowConfetti(true);
      queryClient.invalidateQueries({ queryKey: ['weekly-tasks'] });
      queryClient.invalidateQueries({ queryKey: ['coach-streak'] });

      const nextCompleted = completedCount + 1;
      const isVerified = updatedTask.verificationStatus === 'VERIFIED';

      if (isVerified) {
        showToast({
          type: 'success',
          title: 'Task Verified! 🛡️',
          message: 'Evidence automatically verified from your work!',
        });
      } else if (nextCompleted === 2) {
        showToast({
          type: 'success',
          title: '🎉 Streak Secured! (2 of 3 Done)',
          message: 'Awesome work! You reached your weekly target and secured your consistency streak!',
        });
      } else if (nextCompleted >= 3) {
        showToast({
          type: 'success',
          title: '🏆 Perfect Week Completed!',
          message: 'All 3 weekly coaching tasks completed! You are operating at peak consistency!',
        });
      } else {
        showToast({
          type: 'success',
          title: 'Task Completed! 🎉',
          message: 'Great job staying consistent! 1 down, 1 more to reach your weekly streak goal.',
        });
      }
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Action Failed',
        message: err.message || 'Could not mark task as completed.',
      });
    },
  });

  // Calculate formatted week date range (e.g. "Oct 20 - Oct 26")
  const getWeekDateRange = (weekStartStr?: string): string => {
    let monday: Date;
    if (weekStartStr && /^\d{4}-\d{2}-\d{2}$/.test(weekStartStr)) {
      const [y, m, d] = weekStartStr.split('-').map(Number);
      monday = new Date(y, m - 1, d);
    } else {
      const now = new Date();
      const day = now.getDay();
      const diff = (day === 0 ? -6 : 1) - day;
      monday = new Date(now);
      monday.setDate(now.getDate() + diff);
    }
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);

    const optMonth: Intl.DateTimeFormatOptions = { month: 'short' };
    const mondayMonth = monday.toLocaleDateString('en-US', optMonth);
    const sundayMonth = sunday.toLocaleDateString('en-US', optMonth);

    return `${mondayMonth} ${monday.getDate()} - ${sundayMonth} ${sunday.getDate()}`;
  };

  const weekRange = getWeekDateRange(tasks[0]?.weekStart);

  return (
    <div className="space-y-8">
      {/* Confetti Explosion Overlay */}
      <Confetti active={showConfetti} onComplete={() => setShowConfetti(false)} />

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <CalendarCheck2 className="w-7 h-7 text-indigo-600" />
            Weekly Consistency Coach
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Conquer exactly 3 small, achievable tasks each week to build career momentum and maintain your streak.
          </p>
        </div>

        <div className="flex flex-wrap sm:flex-nowrap items-center gap-2 w-full sm:w-auto">
          {tasks.length > 0 && (
            <button
              type="button"
              disabled={generatePlanMutation.isPending}
              onClick={() => generatePlanMutation.mutate(true)}
              className="w-full sm:w-auto justify-center inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-300 shadow-2xs transition disabled:opacity-60 cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Regenerate Plan
            </button>
          )}

          <button
            type="button"
            disabled={generatePlanMutation.isPending}
            onClick={() => generatePlanMutation.mutate(false)}
            className="w-full sm:w-auto justify-center inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm disabled:opacity-60 transition cursor-pointer"
          >
            {generatePlanMutation.isPending ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4" />
            )}
            Generate Plan
          </button>
        </div>
      </div>

      {/* Streak & Consistency Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Streak Counter */}
        <div className="bg-gradient-to-br from-amber-500 to-orange-600 rounded-2xl p-6 text-white shadow-md flex items-center justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-widest text-amber-100 flex items-center gap-1">
              <Flame className="w-4 h-4 text-amber-200" />
              Consistency Streak
            </span>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-4xl font-black">
                {streak?.activeStreakWeeks ?? streak?.currentStreakWeeks ?? 0}
              </span>
              <span className="text-sm font-semibold text-amber-100">consecutive weeks</span>
            </div>
            <p className="text-xs text-amber-100/90 mt-1">
              {(streak?.activeStreakWeeks ?? streak?.currentStreakWeeks ?? 0) > 0
                ? 'Outstanding dedication! Keep the fire burning.'
                : 'Complete 2 of 3 tasks this week to start your streak.'}
            </p>
          </div>
          <div className="w-14 h-14 rounded-2xl bg-white/20 backdrop-blur-xs flex items-center justify-center text-white">
            <Trophy className="w-7 h-7" />
          </div>
        </div>

        {/* Weekly Target Progress */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Weekly Target (2+ tasks)
            </span>
            <span
              className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
                completedCount >= 2
                  ? 'bg-emerald-100 text-emerald-800'
                  : 'bg-amber-100 text-amber-800'
              }`}
            >
              {completedCount >= 2 ? 'Goal Achieved' : 'In Progress'}
            </span>
          </div>

          <div className="my-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-slate-900">{completedCount}</span>
            <span className="text-sm font-semibold text-slate-400">/ 3 completed</span>
          </div>

          <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
            <div
              className={`h-2.5 rounded-full transition-all duration-500 ${
                completedCount >= 2 ? 'bg-emerald-500' : 'bg-indigo-600'
              }`}
              style={{ width: `${(completedCount / 3) * 100}%` }}
            />
          </div>
        </div>

        {/* Current Cycle Information */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Current Cycle
            </span>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
              {weekRange}
            </span>
          </div>
          <div className="my-2 space-y-1">
            <div className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
              <Calendar className="w-4 h-4 text-indigo-600" />
              {weekRange}
            </div>
            <p className="text-xs text-slate-500">
              Bite-sized fresher milestones structured to build real engineering proof in 1 to 2 hours each.
            </p>
          </div>
          <div className="text-xs font-semibold text-indigo-600 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-indigo-500" />
            New plan every Monday
          </div>
        </div>
      </div>

      {/* Celebration Message Banner (When 2 of 3 tasks are done) */}
      {completedCount >= 2 && (
        <div className="bg-gradient-to-r from-emerald-600 via-teal-600 to-indigo-600 rounded-2xl p-5 text-white shadow-md flex flex-col sm:flex-row sm:items-center justify-between gap-4 animate-in fade-in slide-in-from-top-4 duration-300">
          <div className="flex items-start sm:items-center gap-3.5">
            <div className="w-12 h-12 rounded-xl bg-white/20 backdrop-blur-xs flex items-center justify-center text-white flex-shrink-0">
              <PartyPopper className="w-6 h-6 text-amber-300 animate-bounce" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white">
                  Weekly Consistency Goal Achieved! 🎉
                </h3>
                <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-white/20 text-emerald-100">
                  {completedCount === 3 ? '3 of 3 Perfect Week' : '2 of 3 Completed'}
                </span>
              </div>
              <p className="text-xs text-white/90 mt-1 leading-relaxed">
                {completedCount >= 3
                  ? 'Incredible! You completed all 3 tasks this week. Your engineering momentum is unstoppable!'
                  : 'Outstanding job! You’ve completed 2 of 3 tasks this week. Your weekly consistency streak is officially secured!'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 self-end sm:self-center">
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/20 text-xs font-bold text-white backdrop-blur-xs">
              <Trophy className="w-4 h-4 text-amber-300" />
              Streak Protected
            </span>
          </div>
        </div>
      )}

      {/* Tasks Checklist */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900">This Week's 3 Action Tasks</h2>
          <span className="text-xs text-slate-500">Each task takes ~1 to 2 hours</span>
        </div>

        {isTasksLoading ? (
          <CardSkeleton count={3} />
        ) : tasks.length === 0 ? (
          <EmptyState
            icon={CalendarCheck2}
            title="No coaching tasks for this week yet"
            description="Generate a personalized plan containing exactly 3 achievable tasks based on your profile audit and skill gaps."
            actionText="Generate Plan"
            onAction={() => generatePlanMutation.mutate(false)}
          />
        ) : (
          <div className="space-y-4">
            {tasks.map((task, idx) => {
              const isDone = task.status === 'DONE';
              const isVerified = task.verificationStatus === 'VERIFIED';
              const isSelfReported = task.verificationStatus === 'SELF_REPORTED';

              return (
                <div
                  key={task.id}
                  className={`p-6 rounded-2xl border transition-all duration-200 flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                    isDone
                      ? 'bg-emerald-50/50 border-emerald-200 shadow-2xs'
                      : 'bg-white border-slate-200 shadow-xs hover:border-indigo-300'
                  }`}
                >
                  <div className="flex items-start gap-4 flex-1">
                    <span
                      className={`w-7 h-7 rounded-xl flex items-center justify-center font-bold text-xs flex-shrink-0 mt-0.5 ${
                        isDone
                          ? isVerified
                            ? 'bg-emerald-600 text-white'
                            : 'bg-slate-600 text-white'
                          : 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                      }`}
                    >
                      {idx + 1}
                    </span>

                    <div className="space-y-2 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3
                          className={`text-base font-bold ${
                            isDone ? 'line-through text-slate-400' : 'text-slate-900'
                          }`}
                        >
                          {task.title}
                        </h3>

                        {/* Estimated Time Badge */}
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
                          <Clock className="w-3 h-3 text-slate-400" />
                          {task.estimatedTime || '~1-2 hours'}
                        </span>

                        {/* Category Badge */}
                        {task.category && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-100">
                            {task.category.replace(/_/g, ' ')}
                          </span>
                        )}

                        {/* Verification Type Hint for Pending Tasks */}
                        {!isDone && task.verificationType && (
                          <>
                            {task.verificationType === 'GITHUB_FILE_EXISTS' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium text-slate-600 bg-slate-100 border border-slate-200">
                                <GitBranch className="w-3 h-3 text-slate-400" />
                                File: {task.verificationHint || 'Repo file'}
                              </span>
                            )}
                            {task.verificationType === 'GITHUB_RECENT_COMMIT' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium text-slate-600 bg-slate-100 border border-slate-200">
                                <GitCommit className="w-3 h-3 text-slate-400" />
                                Recent commit
                              </span>
                            )}
                            {task.verificationType === 'PROFILE_CHANGE' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium text-slate-600 bg-slate-100 border border-slate-200">
                                <UserCheck className="w-3 h-3 text-slate-400" />
                                Profile update
                              </span>
                            )}
                          </>
                        )}
                      </div>

                      <p
                        className={`text-sm leading-relaxed ${
                          isDone ? 'text-slate-400' : 'text-slate-600'
                        }`}
                      >
                        {task.description}
                      </p>

                      {/* Why this matters line */}
                      <div className="p-2.5 rounded-xl bg-indigo-50/70 border border-indigo-100 text-xs text-indigo-900 leading-relaxed flex items-start gap-2">
                        <Lightbulb className="w-4 h-4 text-indigo-600 flex-shrink-0 mt-0.5" />
                        <div>
                          <span className="font-bold text-indigo-950">Why this matters: </span>
                          <span>
                            {task.whyThisMatters ||
                              'Builds hands-on technical evidence and showcases proactive engineering growth.'}
                          </span>
                        </div>
                      </div>

                      {task.completedAt && (
                        <p className="text-[11px] text-emerald-700 font-medium pt-1">
                          Completed on {new Date(task.completedAt).toLocaleDateString()}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="flex-shrink-0 self-end sm:self-center">
                    {isDone ? (
                      <div className="flex flex-col sm:items-end gap-1.5">
                        {isVerified ? (
                          <>
                            <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-emerald-800 bg-emerald-100 border border-emerald-300 shadow-2xs">
                              <ShieldCheck className="w-4 h-4 text-emerald-600" />
                              Verified
                            </div>
                            {task.proofUrl && (
                              task.proofUrl.startsWith('http') ? (
                                <a
                                  href={task.proofUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 hover:text-emerald-900 underline transition"
                                >
                                  <ExternalLink className="w-3 h-3" />
                                  View Repo Proof
                                </a>
                              ) : (
                                <span className="text-[11px] font-semibold text-emerald-700">
                                  Profile Updated ✓
                                </span>
                              )
                            )}
                          </>
                        ) : isSelfReported ? (
                          <div className="flex flex-col sm:items-end">
                            <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-slate-700 bg-slate-100 border border-slate-300">
                              <CheckCircle2 className="w-4 h-4 text-slate-500" />
                              Self-Reported
                            </div>
                            <span className="text-[10px] text-slate-400 font-medium mt-0.5">Not verified</span>
                          </div>
                        ) : (
                          <div className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-emerald-800 bg-emerald-100 border border-emerald-300">
                            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                            Completed
                          </div>
                        )}
                      </div>
                    ) : (
                      <button
                        type="button"
                        disabled={completeTaskMutation.isPending}
                        onClick={() => completeTaskMutation.mutate(task.id)}
                        className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition disabled:opacity-60 cursor-pointer"
                        aria-label={`Mark ${task.title} as complete`}
                      >
                        <CheckCircle2 className="w-4 h-4" />
                        Mark Complete
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
