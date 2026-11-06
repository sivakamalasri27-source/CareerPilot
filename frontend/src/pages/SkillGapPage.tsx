import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { roleApi } from '../api/roleApi';
import { agentApi } from '../api/agentApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import {
  Target,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  BookOpen,
  FolderGit2,
  Loader2,
  Sparkles,
} from 'lucide-react';

export const SkillGapPage: React.FC = () => {
  const { showToast } = useToast();
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);

  // Queries
  const { data: roles } = useQuery({
    queryKey: ['roles'],
    queryFn: roleApi.listTargetRoles,
  });

  // Select first role by default once loaded
  if (roles && roles.length > 0 && selectedRoleId === null) {
    setSelectedRoleId(roles[0].id);
  }

  // Skill Gap Analysis Mutation
  const gapMutation = useMutation({
    mutationFn: (roleId: number) => agentApi.analyzeSkillGap(roleId),
    onSuccess: (data) => {
      const pct = typeof data.matchPercentage === 'number' ? data.matchPercentage.toFixed(1) : '—';
      const title = typeof data.roleTitle === 'string' ? data.roleTitle : 'selected role';
      showToast({
        type: 'success',
        title: 'Skill Gap Computed',
        message: `Matched ${pct}% of required skills for ${title}.`,
      });
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Analysis Failed',
        message: err.message || 'Could not analyze skill gap.',
      });
    },
  });

  const gapResult = gapMutation.data;

  const handleAnalyze = () => {
    if (selectedRoleId) {
      gapMutation.mutate(selectedRoleId);
    }
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <Target className="w-7 h-7 text-indigo-600" />
            Curated Role Skill Gap & Roadmap
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Compare your profile against standard industry roles to calculate your match score and generate a roadmap.
          </p>
        </div>

        {/* Role Selector & Trigger */}
        <div className="flex flex-wrap sm:flex-nowrap items-center gap-2 sm:gap-3 w-full sm:w-auto">
          <select
            value={selectedRoleId ?? ''}
            onChange={(e) => setSelectedRoleId(Number(e.target.value))}
            className="w-full sm:w-auto px-3.5 py-2 text-sm rounded-xl border border-slate-300 bg-white font-medium text-slate-800 focus:ring-indigo-500 focus:border-indigo-500 shadow-xs cursor-pointer"
          >
            {roles?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name} ({r.requiredSkillsCount} skills)
              </option>
            ))}
          </select>

          <button
            type="button"
            disabled={!selectedRoleId || gapMutation.isPending}
            onClick={handleAnalyze}
            className="w-full sm:w-auto justify-center inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm disabled:opacity-60 transition cursor-pointer"
          >
            {gapMutation.isPending ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4" />
            )}
            Analyze Gap
          </button>
        </div>
      </div>

      {/* Primary Results Display */}
      {gapMutation.isPending ? (
        <CardSkeleton count={2} />
      ) : !gapResult ? (
        <EmptyState
          icon={Target}
          title="No skill gap analysis generated yet"
          description="Select a target role from the dropdown above and click 'Analyze Gap' to see your match percentage and learning roadmap."
          actionText="Run Analysis"
          onAction={handleAnalyze}
        />
      ) : (
        <div className="space-y-8">
          {/* Match Score Banner */}
          <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div>
                <span className="text-xs font-bold text-indigo-600 uppercase tracking-widest">
                  Target Role: {typeof gapResult.roleTitle === 'string' ? gapResult.roleTitle : '—'}
                </span>
                <h2 className="text-2xl font-extrabold text-slate-900 mt-1">
                  Overall Skill Alignment
                </h2>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-4xl font-black text-indigo-600">
                  {typeof gapResult.matchPercentage === 'number' ? gapResult.matchPercentage.toFixed(1) : '—'}%
                </span>
                <span className="text-xs font-semibold text-slate-400 uppercase leading-tight">
                  Weighted<br />Match
                </span>
              </div>
            </div>

            <div className="w-full bg-slate-100 rounded-full h-3 overflow-hidden">
              <div
                className={`h-3 rounded-full transition-all duration-700 ${
                  (gapResult.matchPercentage ?? 0) >= 75
                    ? 'bg-emerald-500'
                    : (gapResult.matchPercentage ?? 0) >= 50
                    ? 'bg-amber-500'
                    : 'bg-rose-500'
                }`}
                style={{ width: `${Math.max(5, gapResult.matchPercentage ?? 0)}%` }}
              />
            </div>
          </div>

          {/* Matched vs Weak vs Missing Skills Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Matched Skills */}
            <div className="bg-white rounded-2xl border border-emerald-200/80 p-6 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-emerald-900 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  Matched Skills ({gapResult.matchedSkills?.length || 0})
                </h3>
                <span className="text-[10px] font-bold bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded-full">
                  100% Credit
                </span>
              </div>
              <div className="flex flex-wrap gap-2 pt-1">
                {Array.isArray(gapResult.matchedSkills) && gapResult.matchedSkills.length > 0 ? (
                  gapResult.matchedSkills.map((sk, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 rounded-lg bg-emerald-50 border border-emerald-200 text-xs font-semibold text-emerald-800"
                    >
                      {typeof sk === 'string' ? sk : JSON.stringify(sk)}
                    </span>
                  ))
                ) : (
                  <p className="text-xs text-slate-400 italic">No skills matched yet.</p>
                )}
              </div>
            </div>

            {/* Weak Skills */}
            <div className="bg-white rounded-2xl border border-amber-200/80 p-6 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-amber-900 flex items-center gap-1.5">
                  <AlertTriangle className="w-4 h-4 text-amber-600" />
                  Weak Skills ({gapResult.weakSkills?.length || 0})
                </h3>
                <span className="text-[10px] font-bold bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full">
                  50% Partial Credit
                </span>
              </div>
              <p className="text-[11px] text-slate-500">
                Claimed in profile, but GitHub verification found 0 code evidence.
              </p>
              <div className="flex flex-wrap gap-2 pt-1">
                {Array.isArray(gapResult.weakSkills) && gapResult.weakSkills.length > 0 ? (
                  gapResult.weakSkills.map((sk, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 rounded-lg bg-amber-50 border border-amber-200 text-xs font-semibold text-amber-800"
                    >
                      {typeof sk === 'string' ? sk : JSON.stringify(sk)}
                    </span>
                  ))
                ) : (
                  <p className="text-xs text-slate-400 italic">None</p>
                )}
              </div>
            </div>

            {/* Missing Skills */}
            <div className="bg-white rounded-2xl border border-rose-200/80 p-6 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-rose-900 flex items-center gap-1.5">
                  <XCircle className="w-4 h-4 text-rose-600" />
                  Missing Skills ({gapResult.missingSkills?.length || 0})
                </h3>
                <span className="text-[10px] font-bold bg-rose-100 text-rose-800 px-2 py-0.5 rounded-full">
                  0% Credit
                </span>
              </div>
              <p className="text-[11px] text-slate-500">
                Required for this role, but not present in your profile.
              </p>
              <div className="flex flex-wrap gap-2 pt-1">
                {Array.isArray(gapResult.missingSkills) && gapResult.missingSkills.length > 0 ? (
                  gapResult.missingSkills.map((sk, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 rounded-lg bg-rose-50 border border-rose-200 text-xs font-semibold text-rose-800"
                    >
                      {typeof sk === 'string' ? sk : JSON.stringify(sk)}
                    </span>
                  ))
                ) : (
                  <p className="text-xs text-emerald-600 font-medium">All role skills acquired!</p>
                )}
              </div>
            </div>
          </div>

          {/* Prioritized Learning Roadmap */}
          <div className="space-y-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                <BookOpen className="w-5 h-5 text-indigo-600" />
                Prioritized Action Roadmap
              </h2>
              <p className="text-xs text-slate-500">
                Structured learning trajectory synthesized for your missing and weak competencies
              </p>
            </div>

            {!Array.isArray(gapResult.roadmap) || gapResult.roadmap.length === 0 ? (
              <div className="p-6 bg-slate-50 rounded-2xl text-center text-slate-500 text-sm">
                No roadmap items required. Your skills match this role profile!
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {gapResult.roadmap.map((item, idx) => {
                  const skillName = typeof item?.skillName === 'string' ? item.skillName : '—';
                  const priority = typeof item?.priority === 'string' ? item.priority : '';
                  const whyItMatters = typeof item?.whyItMatters === 'string' ? item.whyItMatters : '';
                  const whatToLearn = typeof item?.whatToLearn === 'string' ? item.whatToLearn : '';
                  const projectIdea = typeof item?.projectIdea === 'string' ? item.projectIdea : '';
                  return (
                  <div
                    key={idx}
                    className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between space-y-4 hover:border-indigo-200 transition"
                  >
                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <h3 className="text-base font-bold text-slate-900">{skillName}</h3>
                        <span
                          className={`px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase ${
                            priority === 'CORE'
                              ? 'bg-rose-100 text-rose-800 border border-rose-200'
                              : priority === 'IMPORTANT'
                              ? 'bg-amber-100 text-amber-800 border border-amber-200'
                              : 'bg-blue-100 text-blue-800 border border-blue-200'
                          }`}
                        >
                          {priority}
                        </span>
                      </div>

                      <div className="space-y-2 text-xs text-slate-700 leading-relaxed">
                        <div>
                          <span className="font-semibold text-slate-900">Why it matters: </span>
                          <span>{whyItMatters}</span>
                        </div>
                        <div>
                          <span className="font-semibold text-slate-900">What to learn: </span>
                          <span>{whatToLearn}</span>
                        </div>
                      </div>
                    </div>

                    <div className="pt-3 border-t border-slate-100 bg-indigo-50/40 p-3 rounded-xl border border-indigo-100 text-xs">
                      <div className="flex items-center gap-1.5 font-bold text-indigo-900 mb-1">
                        <FolderGit2 className="w-3.5 h-3.5 text-indigo-600" />
                        Recommended Portfolio Project Idea
                      </div>
                      <p className="text-slate-700 leading-relaxed">{projectIdea}</p>
                    </div>
                  </div>
                ); })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
