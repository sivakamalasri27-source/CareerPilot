import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentApi } from '../api/agentApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import {
  Users,
  Briefcase,
  Rocket,
  Code2,
  CheckCircle2,
  AlertOctagon,
  Clock,
  Sparkles,
  Loader2,
} from 'lucide-react';
import { RecruiterDecision } from '../types/agent';

export const RecruiterSimPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [targetRole, setTargetRole] = useState('');

  // Fetch latest audit report to view recent recruiter simulation feedback
  const { data: reportsPage, isLoading: isReportsLoading } = useQuery({
    queryKey: ['reports'],
    queryFn: () => agentApi.getReports(0, 1),
  });

  const latestId = reportsPage?.content?.[0]?.id;

  const { data: report, isLoading: isReportLoading } = useQuery({
    queryKey: ['report', latestId],
    queryFn: () => agentApi.getReportById(latestId!),
    enabled: !!latestId,
  });

  // Run Recruiter Simulation Mutation
  const simMutation = useMutation({
    mutationFn: () => agentApi.runRecruiterSimulation(targetRole || undefined),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: ['report'] });
      showToast({
        type: 'success',
        title: 'Simulation Complete',
        message: `Recruiter panel reached decision: ${data.overallDecision}`,
      });
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Simulation Failed',
        message: err.message || 'Could not run recruiter simulation.',
      });
    },
  });

  const personas = Array.isArray(report?.recruiterFeedback) ? report.recruiterFeedback : [];

  const getDecisionBadge = (decision: RecruiterDecision) => {
    switch (decision) {
      case 'YES':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-emerald-100 text-emerald-800 border border-emerald-300 flex items-center gap-1 shadow-xs">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            DECISION: YES
          </span>
        );
      case 'MAYBE':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-amber-100 text-amber-800 border border-amber-300 flex items-center gap-1 shadow-xs">
            <Clock className="w-3.5 h-3.5 text-amber-600" />
            DECISION: MAYBE
          </span>
        );
      case 'NO':
      default:
        return (
          <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-rose-100 text-rose-800 border border-rose-300 flex items-center gap-1 shadow-xs">
            <AlertOctagon className="w-3.5 h-3.5 text-rose-600" />
            DECISION: NO
          </span>
        );
    }
  };

  const getPersonaIcon = (name: string) => {
    const lower = name.toLowerCase();
    if (lower.includes('hr') || lower.includes('mnc')) {
      return <Briefcase className="w-5 h-5 text-blue-600" />;
    }
    if (lower.includes('founder') || lower.includes('startup')) {
      return <Rocket className="w-5 h-5 text-purple-600" />;
    }
    return <Code2 className="w-5 h-5 text-indigo-600" />;
  };

  return (
    <div className="space-y-8">
      {/* Header & Role Input */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <Users className="w-7 h-7 text-indigo-600" />
            Recruiter Simulation Panel
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Real-time simulated feedback from an MNC HR Recruiter, Startup Founder, and Technical Lead.
          </p>
        </div>

        <div className="flex flex-wrap sm:flex-nowrap items-center gap-2 sm:gap-3 w-full sm:w-auto">
          <input
            type="text"
            value={targetRole}
            onChange={(e) => setTargetRole(e.target.value)}
            placeholder="Target role (e.g. Java Engineer)"
            className="w-full sm:w-56 px-3.5 py-2 text-xs rounded-xl border border-slate-300 focus:ring-indigo-500 focus:border-indigo-500 shadow-xs"
          />
          <button
            type="button"
            disabled={simMutation.isPending}
            onClick={() => simMutation.mutate()}
            className="w-full sm:w-auto justify-center inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm disabled:opacity-60 transition cursor-pointer"
          >
            {simMutation.isPending ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4" />
            )}
            Run Simulation
          </button>
        </div>
      </div>

      {isReportsLoading || isReportLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <CardSkeleton count={3} />
        </div>
      ) : personas.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No recruiter simulation run yet"
          description="Simulate how different hiring managers evaluate your profile in 10 seconds."
          actionText="Run Recruiter Simulation"
          onAction={() => simMutation.mutate()}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {personas.map((p, idx) => (
            <div
              key={idx}
              className="bg-white rounded-2xl border border-slate-200 shadow-xs flex flex-col justify-between overflow-hidden hover:border-slate-300 transition"
            >
              {/* Persona Header */}
              <div className="p-6 border-b border-slate-100 bg-slate-50/50">
                <div className="flex items-center justify-between">
                  <div className="p-2.5 rounded-xl bg-white border border-slate-200 shadow-2xs">
                    {getPersonaIcon(p.persona)}
                  </div>
                  {getDecisionBadge(p.decision)}
                </div>
                <h3 className="text-base font-bold text-slate-900 mt-4">{p.persona}</h3>
              </div>

              {/* Persona Content */}
              <div className="p-6 space-y-5 flex-1">
                {/* 10-Second Impression */}
                <div>
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                    <Clock className="w-3.5 h-3.5 text-slate-400" />
                    10-Second Impression
                  </span>
                  <p className="mt-1.5 text-xs text-slate-700 italic bg-slate-50 p-3 rounded-xl border border-slate-200/60 leading-relaxed">
                    "{p.firstImpression}"
                  </p>
                </div>

                {/* Strengths */}
                <div>
                  <span className="text-[11px] font-bold text-emerald-700 uppercase tracking-wider flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                    Key Strengths ({p.strengths?.length || 0})
                  </span>
                  <ul className="mt-2 space-y-1.5 text-xs text-slate-700">
                    {p.strengths && p.strengths.length > 0 ? (
                      p.strengths.map((str, sIdx) => (
                        <li key={sIdx} className="flex items-start gap-1.5">
                          <span className="text-emerald-500 font-bold mt-0.5">•</span>
                          <span>{str}</span>
                        </li>
                      ))
                    ) : (
                      <li className="text-slate-400 italic">No standout strengths noted.</li>
                    )}
                  </ul>
                </div>

                {/* Red Flags */}
                <div>
                  <span className="text-[11px] font-bold text-rose-700 uppercase tracking-wider flex items-center gap-1">
                    <AlertOctagon className="w-3.5 h-3.5 text-rose-600" />
                    Red Flags & Concerns ({p.redFlags?.length || 0})
                  </span>
                  <ul className="mt-2 space-y-1.5 text-xs text-slate-700">
                    {p.redFlags && p.redFlags.length > 0 ? (
                      p.redFlags.map((flag, fIdx) => (
                        <li key={fIdx} className="flex items-start gap-1.5">
                          <span className="text-rose-500 font-bold mt-0.5">•</span>
                          <span>{flag}</span>
                        </li>
                      ))
                    ) : (
                      <li className="text-emerald-600 text-xs font-medium">None detected!</li>
                    )}
                  </ul>
                </div>
              </div>

              {/* Reasoning Footer */}
              <div className="p-4 bg-slate-50 border-t border-slate-100 text-xs text-slate-600">
                <span className="font-semibold text-slate-800">Final Verdict Reasoning: </span>
                {p.reasoning}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
