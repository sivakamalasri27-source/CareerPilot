import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentApi } from '../api/agentApi';
import { profileApi } from '../api/profileApi';
import { useToast } from '../context/ToastContext';
import { SkillBadge } from '../components/common/SkillBadge';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import {
  ShieldCheck,
  Github,
  ExternalLink,
  RotateCcw,
  Code,
  FolderGit2,
  AlertOctagon,
  Loader2,
  AlertTriangle,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { VerifiedSkillDto } from '../types/agent';

export const SkillVerifyPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  // 1. Fetch user profile
  const { data: profile, isLoading: isProfileLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: profileApi.getProfile,
  });

  // 2. Fetch user skills catalog from profile if available
  const { data: skillsPage } = useQuery({
    queryKey: ['profile', 'skills'],
    queryFn: async () => {
      if (typeof profileApi.getSkills === 'function') {
        try {
          return await profileApi.getSkills(0, 50);
        } catch {
          return null;
        }
      }
      return null;
    },
    enabled: typeof profileApi.getSkills === 'function' && !!profile,
  });

  // 3. Initial verification query — treats 404 or empty as null (empty state), never stuck loading
  const {
    data: initialVerifyData,
    isLoading: isInitialLoading,
    isError: isInitialError,
    error: initialError,
  } = useQuery({
    queryKey: ['skills-verify'],
    queryFn: async () => {
      try {
        return await agentApi.verifySkills();
      } catch (err: any) {
        const status = err?.status ?? err?.response?.status;
        // Treat 404 (no previous result or not found) as empty state, not a fatal failure
        if (status === 404) {
          return null;
        }
        throw err;
      }
    },
    enabled: !!profile?.githubUsername,
    retry: false,
  });

  // 4. Verify Skills Mutation
  const verifyMutation = useMutation({
    mutationFn: agentApi.verifySkills,
    onSuccess: (data) => {
      queryClient.setQueryData(['skills-verify'], data);
      queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });

      const verified =
        typeof data?.verifiedCount === 'number'
          ? data.verifiedCount
          : (data as any)?.verifiedSkillsCount ?? 0;
      const partial = typeof data?.partialCount === 'number' ? data.partialCount : 0;
      const unverified = typeof data?.unverifiedCount === 'number' ? data.unverifiedCount : 0;

      showToast({
        type: 'success',
        title: 'Verification Complete',
        message: `Audited against GitHub: ${verified} verified, ${partial} partial, ${unverified} unverified.`,
      });
    },
    onError: (err: any) => {
      const msg = formatErrorMessage(err);
      showToast({
        type: 'error',
        title: 'Verification Failed',
        message: msg,
      });
    },
  });

  // Combine query and mutation results
  const verifyData = verifyMutation.data || initialVerifyData;

  // Normalize skills list across various backend/mock shapes
  const extractSkills = (data: any): VerifiedSkillDto[] => {
    if (!data) return [];
    if (Array.isArray(data.skills)) return data.skills;
    if (Array.isArray(data.agentResult?.data?.skills)) return data.agentResult.data.skills;
    return [];
  };

  const skills = extractSkills(verifyData);

  const verifiedCount =
    typeof verifyData?.verifiedCount === 'number'
      ? verifyData.verifiedCount
      : typeof (verifyData as any)?.verifiedSkillsCount === 'number'
      ? (verifyData as any).verifiedSkillsCount
      : skills.filter((s) => s.status === 'VERIFIED').length;

  const partialCount =
    typeof verifyData?.partialCount === 'number'
      ? verifyData.partialCount
      : skills.filter((s) => s.status === 'PARTIAL').length;

  const unverifiedCount =
    typeof verifyData?.unverifiedCount === 'number'
      ? verifyData.unverifiedCount
      : skills.filter((s) => s.status === 'UNVERIFIED').length;

  const summary =
    verifyData?.summary ||
    (verifyData as any)?.agentResult?.data?.summary ||
    '';

  // Format backend errors into friendly messages including GitHub 404 and rate limit (403/429)
  const formatErrorMessage = (err: any): string => {
    if (!err) return 'An unexpected error occurred during verification.';
    const status = err?.status ?? err?.response?.status;
    const rawMessage = err?.message || err?.response?.data?.message || '';
    const lower = rawMessage.toLowerCase();

    if (status === 404 || lower.includes('not found') || lower.includes('404')) {
      return `GitHub user "${profile?.githubUsername}" not found. Please verify your GitHub username in your Profile Builder.`;
    }
    if (
      status === 403 ||
      status === 429 ||
      lower.includes('rate limit') ||
      lower.includes('429') ||
      lower.includes('403')
    ) {
      return 'GitHub API rate limit exceeded. Please wait a few minutes before retrying verification.';
    }
    return rawMessage || 'Could not verify skills against GitHub. Please try again.';
  };

  const activeError = verifyMutation.error || (isInitialError ? initialError : null);

  // Check if profile has claimed skills
  const hasNoSkillsClaimed =
    skillsPage !== undefined &&
    skillsPage !== null &&
    skillsPage.totalElements === 0 &&
    !skills.length &&
    (!profile || !Array.isArray((profile as any).skills) || (profile as any).skills.length === 0);

  const isRunning = verifyMutation.isPending;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <ShieldCheck className="w-7 h-7 text-indigo-600" />
            GitHub Proof-of-Skill Verification
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            ProofOfSkillAgent scans your public repositories, languages, topics, and code to verify claimed skills.
          </p>
        </div>

        <button
          type="button"
          disabled={isRunning || !profile?.githubUsername}
          onClick={() => verifyMutation.mutate()}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm disabled:opacity-60 transition cursor-pointer"
        >
          <RotateCcw className={`w-4 h-4 ${isRunning ? 'animate-spin' : ''}`} />
          {isRunning ? 'Verifying Skills...' : 'Run Verification'}
        </button>
      </div>

      {/* GitHub Username Banner */}
      {!profile?.githubUsername ? (
        <div className="p-6 rounded-2xl bg-amber-50 border border-amber-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <Github className="w-6 h-6 text-amber-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="text-sm font-bold text-amber-900">GitHub username not connected</h3>
              <p className="text-xs text-amber-800 mt-0.5">
                To enable automatic skill verification and repository evidence extraction, add your GitHub username.
              </p>
            </div>
          </div>
          <Link
            to="/profile"
            className="px-4 py-2 rounded-xl text-xs font-bold text-amber-900 bg-amber-200 hover:bg-amber-300 transition whitespace-nowrap"
          >
            Connect GitHub in Profile
          </Link>
        </div>
      ) : hasNoSkillsClaimed ? (
        <div className="p-6 rounded-2xl bg-blue-50 border border-blue-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <Code className="w-6 h-6 text-blue-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="text-sm font-bold text-blue-900">No skills added to profile yet</h3>
              <p className="text-xs text-blue-800 mt-0.5">
                Add your technical skills in the Profile Builder so ProofOfSkillAgent can scan your repositories and verify them.
              </p>
            </div>
          </div>
          <Link
            to="/profile"
            className="px-4 py-2 rounded-xl text-xs font-bold text-blue-900 bg-blue-200 hover:bg-blue-300 transition whitespace-nowrap"
          >
            Add Skills in Profile
          </Link>
        </div>
      ) : (
        <div className="p-4 rounded-xl bg-slate-900 text-white flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center">
              <Github className="w-4 h-4" />
            </div>
            <div>
              <span className="text-xs text-slate-400">Connected GitHub Account</span>
              <p className="text-sm font-bold">github.com/{profile.githubUsername}</p>
            </div>
          </div>
          <a
            href={`https://github.com/${profile.githubUsername}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
          >
            View GitHub Profile
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>
      )}

      {/* Verification In Progress Banner */}
      {isRunning && (
        <div className="p-6 rounded-2xl bg-indigo-50 border border-indigo-200 flex flex-col items-center justify-center text-center space-y-3 animate-in fade-in duration-200">
          <div className="w-12 h-12 rounded-2xl bg-indigo-100 text-indigo-600 flex items-center justify-center">
            <Loader2 className="w-6 h-6 animate-spin text-indigo-600" />
          </div>
          <div className="max-w-md space-y-1">
            <h3 className="text-base font-bold text-indigo-950">
              Verifying Skills against GitHub Repositories...
            </h3>
            <p className="text-xs text-indigo-700 leading-relaxed">
              Scanning public repositories for github.com/{profile?.githubUsername}, checking code files, language distributions, and commit evidence.
            </p>
          </div>
          <div className="w-full max-w-xs bg-indigo-100 rounded-full h-2 overflow-hidden mt-2">
            <div className="bg-indigo-600 h-2 rounded-full animate-pulse" style={{ width: '75%' }} />
          </div>
        </div>
      )}

      {/* Error State with Backend Message and Retry button */}
      {!isRunning && activeError && (
        <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 flex flex-col items-center justify-center text-center space-y-3">
          <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center">
            <AlertOctagon className="w-6 h-6" />
          </div>
          <div className="max-w-md space-y-1">
            <h3 className="text-base font-bold text-rose-900">Verification Failed</h3>
            <p className="text-xs text-rose-700 leading-relaxed">
              {formatErrorMessage(activeError)}
            </p>
          </div>
          <button
            type="button"
            onClick={() => verifyMutation.mutate()}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 transition cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Retry Verification
          </button>
        </div>
      )}

      {/* Summary note if present and rate-limited or not found */}
      {!isRunning && !activeError && summary && (
        <div
          className={`p-4 rounded-xl border text-xs flex items-start gap-2.5 ${
            summary.toLowerCase().includes('rate limit')
              ? 'bg-amber-50 border-amber-200 text-amber-900'
              : summary.toLowerCase().includes('not found')
              ? 'bg-rose-50 border-rose-200 text-rose-900'
              : 'bg-indigo-50/60 border-indigo-100 text-indigo-900'
          }`}
        >
          {summary.toLowerCase().includes('rate limit') ? (
            <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
          ) : (
            <ShieldCheck className="w-4 h-4 text-indigo-600 flex-shrink-0 mt-0.5" />
          )}
          <div className="space-y-0.5">
            <span className="font-bold">Verification Summary: </span>
            <span>{summary}</span>
          </div>
        </div>
      )}

      {/* Metrics Bar */}
      {verifyData && !isRunning && !activeError && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-emerald-800 uppercase">Verified Skills</span>
              <p className="text-2xl font-extrabold text-emerald-900 mt-0.5">{verifiedCount}</p>
            </div>
            <div className="w-9 h-9 rounded-full bg-emerald-200 text-emerald-800 flex items-center justify-center font-bold">
              ✓
            </div>
          </div>

          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-amber-800 uppercase">Partially Verified</span>
              <p className="text-2xl font-extrabold text-amber-900 mt-0.5">{partialCount}</p>
            </div>
            <div className="w-9 h-9 rounded-full bg-amber-200 text-amber-800 flex items-center justify-center font-bold">
              !
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-slate-700 uppercase">Unverified Skills</span>
              <p className="text-2xl font-extrabold text-slate-900 mt-0.5">{unverifiedCount}</p>
            </div>
            <div className="w-9 h-9 rounded-full bg-slate-200 text-slate-700 flex items-center justify-center font-bold">
              ?
            </div>
          </div>
        </div>
      )}

      {/* Skills Evidence List / Loading / Empty states */}
      {isProfileLoading || (isInitialLoading && !verifyData && !activeError) ? (
        <CardSkeleton count={3} />
      ) : isRunning ? null : activeError ? null : skills.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title={profile?.githubUsername ? 'No verification results found' : 'GitHub not connected'}
          description={
            profile?.githubUsername
              ? "Click 'Run Verification' to analyze your claimed skills against public GitHub repositories."
              : 'Add your GitHub username in your profile to enable automatic skill verification.'
          }
          actionText={profile?.githubUsername ? 'Run Verification' : 'Go to Profile'}
          onAction={() => verifyMutation.mutate()}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {skills.map((vs, idx) => (
            <div
              key={idx}
              className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex flex-col justify-between space-y-4"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <Code className="w-4 h-4 text-indigo-600" />
                    {vs.skill}
                  </h3>
                  <SkillBadge
                    status={vs.status}
                    skillName={vs.skill}
                    evidenceUrl={vs.evidence?.[0]?.htmlUrl}
                    evidenceSummary={vs.explanation}
                  />
                </div>

                <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 p-3 rounded-xl border border-slate-100">
                  {vs.explanation}
                </p>
              </div>

              {/* Evidence Repositories */}
              <div className="pt-2 border-t border-slate-100 space-y-2">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                  <FolderGit2 className="w-3.5 h-3.5" />
                  Repository Evidence ({vs.evidence?.length || 0})
                </span>

                {vs.evidence && vs.evidence.length > 0 ? (
                  <div className="space-y-1.5">
                    {vs.evidence.map((ev, eIdx) => (
                      <div
                        key={eIdx}
                        className="p-2.5 rounded-lg border border-slate-200/80 bg-slate-50/50 flex items-center justify-between text-xs"
                      >
                        <div className="truncate mr-2">
                          <span className="font-semibold text-slate-800">{ev.repoName}</span>
                          <p className="text-[11px] text-slate-500 truncate">{ev.reason}</p>
                        </div>
                        {ev.htmlUrl && (
                          <a
                            href={ev.htmlUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-indigo-600 hover:text-indigo-800 p-1 flex-shrink-0"
                            title="Open repository in GitHub"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                          </a>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">
                    No matching public GitHub repository or topic tags found for this skill.
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
