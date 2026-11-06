import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentApi } from '../api/agentApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import {
  FileText,
  Sparkles,
  AlertTriangle,
  History,
  CheckCircle2,
  Calendar,
  Loader2,
  Quote,
} from 'lucide-react';

export const AuditReportPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);

  // Queries
  const { data: reportsPage, isLoading: isReportsLoading } = useQuery({
    queryKey: ['reports'],
    queryFn: () => agentApi.getReports(0, 10),
  });

  const latestId = reportsPage?.content?.[0]?.id;
  const activeReportId = selectedReportId || latestId;

  const { data: report, isLoading: isReportLoading } = useQuery({
    queryKey: ['report', activeReportId],
    queryFn: () => agentApi.getReportById(activeReportId!),
    enabled: !!activeReportId,
  });

  // Run Audit Mutation
  const auditMutation = useMutation({
    mutationFn: (vars?: { force?: boolean }) => agentApi.runFullAudit(undefined, vars?.force),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: ['report'] });
      const reportId = data.reportId ?? data.auditId;
      if (reportId) {
        setSelectedReportId(reportId);
      }
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
          message: `Profile score: ${score ?? 0}/100. New report generated.`,
        });
      }
    },
    onError: (err: { message?: string }) => {
      showToast({
        type: 'error',
        title: 'Audit Failed',
        message: err.message || 'Could not complete audit.',
      });
    },
  });

  const sectionScores = report?.sectionScores || {};

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <FileText className="w-7 h-7 text-indigo-600" />
            Profile Audit Report
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Multi-agent evaluation of each profile section, weakness isolation, and audit history.
          </p>
        </div>
        <button
          type="button"
          disabled={auditMutation.isPending}
          onClick={() => auditMutation.mutate()}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-md shadow-indigo-100 disabled:opacity-60 transition"
        >
          {auditMutation.isPending ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Sparkles className="w-4 h-4" />
          )}
          Run New Audit
        </button>
      </div>

      {auditMutation.isError && (
        <div
          data-testid="audit-error-banner"
          className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 text-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-xs"
        >
          <div className="flex items-center gap-2.5">
            <AlertTriangle className="w-5 h-5 text-rose-600 flex-shrink-0" />
            <span className="font-medium">
              {auditMutation.error?.message || 'AI service unavailable, please retry.'}
            </span>
          </div>
          <button
            type="button"
            onClick={() => auditMutation.mutate()}
            className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 shadow-sm transition flex-shrink-0"
          >
            Retry
          </button>
        </div>
      )}

      {auditMutation.data?.unchanged && (
        <div
          data-testid="audit-unchanged-banner"
          className="p-3.5 rounded-2xl bg-sky-50 border border-sky-200 text-sky-800 text-xs font-semibold flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 shadow-xs"
        >
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-sky-600 flex-shrink-0" />
            <span>No changes since last audit</span>
          </div>
          <button
            type="button"
            onClick={() => auditMutation.mutate({ force: true })}
            className="px-3 py-1 rounded-lg text-xs font-bold text-sky-700 bg-sky-100 hover:bg-sky-200 transition"
          >
            Re-run anyway
          </button>
        </div>
      )}

      {isReportsLoading ? (
        <CardSkeleton count={2} />
      ) : !reportsPage?.content || reportsPage.content.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No audit reports found"
          description="Click 'Run New Audit' to analyze your profile headline, about section, projects, and skills."
          actionText="Run Full Profile Audit"
          onAction={() => auditMutation.mutate()}
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          {/* Main Content Area (Selected Report) */}
          <div className="lg:col-span-8 space-y-6">
            {isReportLoading ? (
              <CardSkeleton count={2} />
            ) : report ? (
              <>
                {/* Score Summary Card */}
                <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs">
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-100">
                    <div>
                      <span className="text-xs font-bold text-indigo-600 uppercase tracking-widest">
                        Audit Report #{report.id}
                      </span>
                      <h2 className="text-xl font-bold text-slate-900 mt-1">
                        Evaluation Breakdown
                      </h2>
                      <div className="flex items-center gap-2 text-xs text-slate-400 mt-1">
                        <Calendar className="w-3.5 h-3.5" />
                        <span>Generated on {new Date(report.createdAt).toLocaleString()}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 bg-slate-50 px-4 py-3 rounded-2xl border border-slate-200">
                      <div className="text-right">
                        <div className="text-xs font-semibold text-slate-500 uppercase">Overall</div>
                        <div className="text-xs text-slate-400">Readiness</div>
                      </div>
                      <div className="text-3xl font-extrabold text-indigo-600">
                        {report.overallScore}
                        <span className="text-xs font-medium text-slate-400">/100</span>
                      </div>
                    </div>
                  </div>

                  {/* Section Scores Grid */}
                  <div className="mt-6 grid grid-cols-2 sm:grid-cols-5 gap-4">
                    {['headline', 'about', 'skills', 'projects', 'education'].map((key) => {
                      const score = sectionScores[key] ?? 0;
                      return (
                        <div key={key} className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-center">
                          <span className="text-[11px] font-bold text-slate-500 uppercase">
                            {key}
                          </span>
                          <div className="text-xl font-extrabold text-slate-900 mt-1">{score}</div>
                          <div className="w-full bg-slate-200 rounded-full h-1.5 mt-2">
                            <div
                              className={`h-1.5 rounded-full ${
                                score >= 75
                                  ? 'bg-emerald-500'
                                  : score >= 50
                                  ? 'bg-amber-500'
                                  : 'bg-rose-500'
                              }`}
                              style={{ width: `${score}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Weaknesses List */}
                <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                        <AlertTriangle className="w-5 h-5 text-amber-500" />
                        Identified Weaknesses & Gaps
                      </h3>
                      <p className="text-xs text-slate-500 mt-0.5">
                        Exact lines of text flagged by the auditor with actionable reasons
                      </p>
                    </div>
                    <span className="px-2.5 py-1 rounded-full bg-amber-50 text-amber-800 text-xs font-bold border border-amber-200">
                      {report.weaknesses?.length || 0} Weaknesses
                    </span>
                  </div>

                  {!report.weaknesses || report.weaknesses.length === 0 ? (
                    <div className="text-center py-8 bg-emerald-50/50 rounded-xl border border-emerald-100 p-4">
                      <CheckCircle2 className="w-8 h-8 mx-auto text-emerald-600 mb-2" />
                      <p className="text-sm font-bold text-emerald-900">No weaknesses detected!</p>
                      <p className="text-xs text-emerald-700 mt-1">
                        All sections satisfy industry quality thresholds.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      {report.weaknesses.map((w, idx) => (
                        <div
                          key={idx}
                          className="p-4 rounded-xl border border-amber-200/80 bg-amber-50/30 space-y-2.5"
                        >
                          <div className="flex items-center justify-between">
                            <span className="px-2.5 py-0.5 rounded-md bg-amber-100 text-amber-900 text-[11px] font-bold uppercase tracking-wider">
                              {w.section}
                            </span>
                            <span className="text-xs text-amber-700 font-medium">Issue #{idx + 1}</span>
                          </div>

                          <div className="p-3 rounded-lg bg-white border border-amber-200/60 text-xs text-slate-800 font-mono flex items-start gap-2">
                            <Quote className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
                            <span className="italic leading-relaxed">{w.weakText}</span>
                          </div>

                          <div className="text-xs text-slate-700 leading-relaxed pl-1">
                            <span className="font-semibold text-slate-900">Why it needs improvement: </span>
                            {w.reason}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            ) : null}
          </div>

          {/* Audit History Sidebar */}
          <div className="lg:col-span-4 space-y-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs space-y-4">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <History className="w-4 h-4 text-indigo-600" />
                Audit Report History
              </h3>

              <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
                {reportsPage.content.map((rep) => {
                  const isSelected = rep.id === activeReportId;
                  return (
                    <button
                      key={rep.id}
                      type="button"
                      onClick={() => setSelectedReportId(rep.id)}
                      className={`w-full py-3 px-3 text-left rounded-xl transition flex items-center justify-between ${
                        isSelected
                          ? 'bg-indigo-50 border border-indigo-200 text-indigo-900'
                          : 'hover:bg-slate-50 text-slate-700'
                      }`}
                    >
                      <div className="space-y-0.5">
                        <div className="text-xs font-bold">Audit #{rep.id}</div>
                        <div className="text-[11px] text-slate-400">
                          {new Date(rep.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-sm font-extrabold text-indigo-600">
                          {rep.overallScore}/100
                        </div>
                        <div className="text-[10px] text-slate-400 font-medium">
                          {rep.requestType}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
