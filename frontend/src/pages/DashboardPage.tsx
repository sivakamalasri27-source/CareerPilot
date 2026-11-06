import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentApi } from '../api/agentApi';
import { coachApi } from '../api/coachApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from 'recharts';
import {
  Sparkles,
  ShieldCheck,
  Target,
  Users,
  Flame,
  CheckCircle2,
  Clock,
  ArrowRight,
  TrendingUp,
  Inbox,
  UploadCloud,
  Loader2,
  Briefcase,
} from 'lucide-react';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  // Queries
  const { data: reportsPage } = useQuery({
    queryKey: ['reports', 'latest'],
    queryFn: () => agentApi.getReports(0, 1),
  });

  const latestReportSummary = reportsPage?.content?.[0];

  const { data: fullReport, isLoading: isFullReportLoading } = useQuery({
    queryKey: ['report', latestReportSummary?.id],
    queryFn: () => agentApi.getReportById(latestReportSummary!.id),
    enabled: !!latestReportSummary?.id,
  });

  const { data: weeklyTasks, isLoading: isTasksLoading } = useQuery({
    queryKey: ['weekly-tasks'],
    queryFn: () => coachApi.getWeeklyTasks(),
  });

  const { data: streak } = useQuery({
    queryKey: ['coach-streak'],
    queryFn: () => coachApi.getStreak(),
  });

  // Run Audit Mutation
  const auditMutation = useMutation({
    mutationFn: () => agentApi.runFullAudit(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: ['report'] });
      queryClient.invalidateQueries({ queryKey: ['weekly-tasks'] });
      const score = data.overallScore ?? data.auditorResult?.data?.overallScore;
      if (data.unchanged) {
        showToast({
          type: 'info',
          title: 'Profile Unchanged',
          message: 'No changes since last audit',
        });
      } else {
        showToast({
          type: 'success',
          title: 'Audit Complete',
          message: score !== undefined ? `Profile score: ${score}/100` : 'Profile analyzed successfully!',
        });
      }
      navigate('/audit');
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Audit Failed',
        message: err.message || 'Failed to complete profile audit.',
      });
    },
  });

  // Complete Task Mutation
  const completeTaskMutation = useMutation({
    mutationFn: (id: number) => coachApi.completeTask(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['weekly-tasks'] });
      queryClient.invalidateQueries({ queryKey: ['coach-streak'] });
      showToast({ type: 'success', message: 'Task marked as completed!' });
    },
    onError: (err: { message?: string }) => {
      showToast({ type: 'error', message: err.message || 'Failed to complete task' });
    },
  });

  // Format Radar Data
  const sectionScores = fullReport?.sectionScores || {
    headline: 0,
    about: 0,
    skills: 0,
    projects: 0,
    education: 0,
  };

  const radarData = [
    { subject: 'Headline', score: sectionScores.headline ?? 0, fullMark: 100 },
    { subject: 'About', score: sectionScores.about ?? 0, fullMark: 100 },
    { subject: 'Skills', score: sectionScores.skills ?? 0, fullMark: 100 },
    { subject: 'Projects', score: sectionScores.projects ?? 0, fullMark: 100 },
    { subject: 'Education', score: sectionScores.education ?? 0, fullMark: 100 },
  ];

  const overallScore = fullReport?.overallScore ?? latestReportSummary?.overallScore ?? 0;

  return (
    <div className="space-y-8">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-indigo-900 via-indigo-800 to-slate-900 text-white p-6 sm:p-8 shadow-xl">
        <div className="relative z-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
          <div className="space-y-2 max-w-xl">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-indigo-500/20 text-indigo-200 text-xs font-semibold backdrop-blur-sm border border-indigo-400/20">
              <Sparkles className="w-3.5 h-3.5 text-indigo-300" />
              Fresher Acceleration Engine
            </span>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
              Candidate Command Center
            </h1>
            <p className="text-sm text-indigo-200 leading-relaxed">
              Track your profile readiness, inspect recruiter impressions, and conquer this week's coaching tasks.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => auditMutation.mutate()}
              disabled={auditMutation.isPending}
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl font-bold text-sm bg-white text-indigo-950 hover:bg-indigo-50 shadow-md transition disabled:opacity-75"
            >
              {auditMutation.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin text-indigo-600" />
              ) : (
                <Sparkles className="w-4 h-4 text-indigo-600" />
              )}
              Run Full Audit
            </button>
            <button
              type="button"
              onClick={() => navigate('/skills-verify')}
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl font-semibold text-sm bg-indigo-700/60 hover:bg-indigo-700 text-white border border-indigo-500/30 transition"
            >
              <ShieldCheck className="w-4 h-4 text-emerald-300" />
              Verify Skills
            </button>
          </div>
        </div>
      </div>

      {/* Primary KPI Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Metric 1: Overall Profile Score */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Profile Readiness
            </span>
            <div className="p-2 rounded-xl bg-indigo-50 text-indigo-600">
              <TrendingUp className="w-5 h-5" />
            </div>
          </div>
          <div className="my-4 flex items-baseline gap-3">
            <span className="text-4xl font-extrabold text-slate-900 tracking-tight">
              {overallScore}
            </span>
            <span className="text-sm font-semibold text-slate-400">/ 100</span>
          </div>
          <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
            <div
              className={`h-2.5 rounded-full transition-all duration-500 ${
                overallScore >= 75
                  ? 'bg-emerald-500'
                  : overallScore >= 50
                  ? 'bg-amber-500'
                  : 'bg-rose-500'
              }`}
              style={{ width: `${Math.min(100, Math.max(5, overallScore))}%` }}
            />
          </div>
          <p className="mt-3 text-xs text-slate-500">
            {overallScore === 0
              ? 'Run an audit to calculate your multi-agent score'
              : overallScore >= 75
              ? 'Strong competitive foundation for freshers'
              : 'Actionable improvements recommended below'}
          </p>
        </div>

        {/* Metric 2: Consistency Streak */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Weekly Consistency Streak
            </span>
            <div className="p-2 rounded-xl bg-amber-50 text-amber-600">
              <Flame className="w-5 h-5" />
            </div>
          </div>
          <div className="my-4 flex items-baseline gap-2">
            <span className="text-4xl font-extrabold text-slate-900 tracking-tight">
              {streak?.currentStreakWeeks ?? streak?.activeStreakWeeks ?? 0}
            </span>
            <span className="text-sm font-semibold text-slate-500">consecutive weeks</span>
          </div>
          <div className="flex items-center justify-between text-xs text-slate-500 bg-slate-50 p-2 rounded-lg border border-slate-100">
            <span>
              This Week:{' '}
              {streak?.currentWeekCompletedTasks ??
                streak?.completedTasksThisWeek ??
                weeklyTasks?.filter((t) => t.status === 'DONE').length ??
                0}
              /3 tasks done
            </span>
            <span
              className={`font-semibold ${
                (streak?.currentWeekGoalMet ??
                streak?.currentWeekMetGoal ??
                (weeklyTasks?.filter((t) => t.status === 'DONE').length || 0) >= 2)
                  ? 'text-emerald-600'
                  : 'text-amber-600'
              }`}
            >
              {(streak?.currentWeekGoalMet ??
              streak?.currentWeekMetGoal ??
              (weeklyTasks?.filter((t) => t.status === 'DONE').length || 0) >= 2)
                ? 'Goal Met (2+)'
                : 'Needs 2+ tasks'}
            </span>
          </div>
          <p className="mt-3 text-xs text-slate-500">
            Complete at least 2 of 3 tasks every week to maintain streak.
          </p>
        </div>

        {/* Metric 3: Quick Navigation */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
            Quick Hub
          </span>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 my-2">
            <button
              type="button"
              onClick={() => navigate('/job-match')}
              className="p-2.5 rounded-xl border border-slate-200 hover:border-emerald-300 hover:bg-emerald-50/50 text-left transition group"
            >
              <Briefcase className="w-4 h-4 text-emerald-600 mb-1" />
              <div className="text-xs font-bold text-slate-800">Job Match</div>
              <div className="text-[10px] text-slate-500">Analyze any JD</div>
            </button>
            <button
              type="button"
              onClick={() => navigate('/skill-gap')}
              className="p-2.5 rounded-xl border border-slate-200 hover:border-indigo-300 hover:bg-indigo-50/50 text-left transition group"
            >
              <Target className="w-4 h-4 text-indigo-600 mb-1" />
              <div className="text-xs font-bold text-slate-800">Skill Gap</div>
              <div className="text-[10px] text-slate-500">Roadmap to role</div>
            </button>
            <button
              type="button"
              onClick={() => navigate('/recruiter-simulation')}
              className="p-2.5 rounded-xl border border-slate-200 hover:border-purple-300 hover:bg-purple-50/50 text-left transition group"
            >
              <Users className="w-4 h-4 text-purple-600 mb-1" />
              <div className="text-xs font-bold text-slate-800">Recruiter Panel</div>
              <div className="text-[10px] text-slate-500">MNC, Founder, Tech</div>
            </button>
            <button
              type="button"
              onClick={() => navigate('/suggestions')}
              className="p-2.5 rounded-xl border border-slate-200 hover:border-amber-300 hover:bg-amber-50/50 text-left transition group"
            >
              <Inbox className="w-4 h-4 text-amber-600 mb-1" />
              <div className="text-xs font-bold text-slate-800">Suggestions</div>
              <div className="text-[10px] text-slate-500">Review & approve</div>
            </button>
            <button
              type="button"
              onClick={() => navigate('/resume')}
              className="p-2.5 rounded-xl border border-slate-200 hover:border-rose-300 hover:bg-rose-50/50 text-left transition group"
            >
              <UploadCloud className="w-4 h-4 text-rose-600 mb-1" />
              <div className="text-xs font-bold text-slate-800">Resume PDF</div>
              <div className="text-[10px] text-slate-500">Extract & translate</div>
            </button>
          </div>
        </div>
      </div>

      {/* Main Grid: Radar Chart + Weekly Tasks */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Radar Chart Section */}
        <div className="lg:col-span-6 bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">Section Score Radar</h2>
              <p className="text-xs text-slate-500">
                Evaluation across Headline, About, Skills, Projects, and Education
              </p>
            </div>
            <button
              type="button"
              onClick={() => navigate('/audit')}
              className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1"
            >
              Audit Details
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="h-72 w-full flex items-center justify-center">
            {isFullReportLoading ? (
              <CardSkeleton />
            ) : overallScore === 0 ? (
              <div className="text-center p-6 text-slate-400 flex flex-col items-center justify-center">
                <Sparkles className="w-10 h-10 mx-auto text-indigo-300 mb-2" />
                <p className="text-sm font-medium text-slate-600">No audit run yet</p>
                <p className="text-xs text-slate-400 mt-1 mb-4">
                  Run a full multi-agent audit to generate your 5-axis competency breakdown.
                </p>
                <button
                  type="button"
                  onClick={() => auditMutation.mutate()}
                  disabled={auditMutation.isPending}
                  className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-700 transition shadow-xs disabled:opacity-50"
                >
                  {auditMutation.isPending ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      Running audit...
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-3.5 h-3.5" />
                      Run your first audit
                    </>
                  )}
                </button>
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <RadarChart cx="50%" cy="50%" outerRadius="75%" data={radarData}>
                  <PolarGrid stroke="#e2e8f0" />
                  <PolarAngleAxis dataKey="subject" tick={{ fill: '#475569', fontSize: 12 }} />
                  <PolarRadiusAxis angle={30} domain={[0, 100]} stroke="#cbd5e1" />
                  <Radar
                    name="Score"
                    dataKey="score"
                    stroke="#4f46e5"
                    fill="#6366f1"
                    fillOpacity={0.4}
                  />
                </RadarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Weekly Coach Tasks Checklist */}
        <div className="lg:col-span-6 bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900">Weekly Action Plan</h2>
                <p className="text-xs text-slate-500">
                  Targeted fresher goals derived from your audit weaknesses
                </p>
              </div>
              <button
                type="button"
                onClick={() => navigate('/coach')}
                className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1"
              >
                Coach Page
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>

            {isTasksLoading ? (
              <CardSkeleton count={3} />
            ) : !weeklyTasks || weeklyTasks.length === 0 ? (
              <div className="text-center py-10 bg-slate-50/60 rounded-xl border border-slate-100">
                <Clock className="w-8 h-8 mx-auto text-slate-400 mb-2" />
                <p className="text-sm font-semibold text-slate-700">No plan generated for this week</p>
                <button
                  type="button"
                  onClick={() => navigate('/coach')}
                  className="mt-3 px-4 py-2 text-xs font-semibold bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition"
                >
                  Generate Plan
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {weeklyTasks.map((task) => {
                  const isDone = task.status === 'DONE';
                  return (
                    <div
                      key={task.id}
                      className={`p-4 rounded-xl border transition flex items-start gap-3.5 ${
                        isDone
                          ? 'bg-emerald-50/40 border-emerald-200/80 text-emerald-900'
                          : 'bg-white border-slate-200 text-slate-800 hover:border-indigo-200'
                      }`}
                    >
                      <button
                        type="button"
                        disabled={isDone || completeTaskMutation.isPending}
                        onClick={() => completeTaskMutation.mutate(task.id)}
                        className={`mt-0.5 flex-shrink-0 w-5 h-5 rounded-md border flex items-center justify-center transition ${
                          isDone
                            ? 'bg-emerald-600 border-emerald-600 text-white'
                            : 'border-slate-300 hover:border-indigo-500 bg-white'
                        }`}
                        aria-label={`Mark task ${task.title} as complete`}
                      >
                        {isDone && <CheckCircle2 className="w-4 h-4 text-white" />}
                      </button>
                      <div className="flex-1 min-w-0">
                        <h4
                          className={`text-sm font-bold leading-snug ${
                            isDone ? 'line-through text-slate-400' : 'text-slate-900'
                          }`}
                        >
                          {task.title}
                        </h4>
                        <p
                          className={`mt-1 text-xs leading-relaxed ${
                            isDone ? 'text-slate-400' : 'text-slate-600'
                          }`}
                        >
                          {task.description}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <span>Goal: 2 of 3 completed</span>
            <span className="font-semibold text-indigo-600">
              {weeklyTasks?.filter((t) => t.status === 'DONE').length || 0} / 3 Completed
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
