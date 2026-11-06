import React, { useState, useEffect } from 'react';
import {
  Briefcase,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  Clock,
  ShieldCheck,
  Building,
  Target,
  ArrowRight,
  Loader2,
  History,
  FileText,
  Lightbulb,
} from 'lucide-react';
import { jobMatchApi } from '../api/jobMatchApi';
import { JobMatchResponse } from '../types/jobMatch';
import { useToast } from '../context/ToastContext';

export const JobMatchPage: React.FC = () => {
  const { showToast } = useToast();

  const [jobDescription, setJobDescription] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [companyName, setCompanyName] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [currentReport, setCurrentReport] = useState<JobMatchResponse | null>(null);
  const [history, setHistory] = useState<JobMatchResponse[]>([]);

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    setIsLoadingHistory(true);
    try {
      const reports = await jobMatchApi.getHistory();
      setHistory(reports);
      if (reports.length > 0 && !currentReport) {
        setCurrentReport(reports[0]);
      }
    } catch (err: any) {
      console.warn('Could not load job match history', err);
    } finally {
      setIsLoadingHistory(false);
    }
  };

  const handleAnalyze = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!jobDescription.trim()) {
      setError('Please paste a job description to analyze.');
      return;
    }
    if (jobDescription.length > 6000) {
      setError('Job description exceeds maximum limit of 6000 characters.');
      return;
    }

    setError(null);
    setIsLoading(true);

    try {
      const result = await jobMatchApi.matchJob({
        jobDescription: jobDescription.trim(),
        jobTitle: jobTitle.trim() || undefined,
        companyName: companyName.trim() || undefined,
      });

      setCurrentReport(result);
      setHistory((prev) => [result, ...prev.filter((item) => item.id !== result.id)]);
      showToast({
        type: 'success',
        title: 'Job Match Analysis Complete',
        message: `Calculated ${result.matchPercentage}% match against job description!`,
      });
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to analyze job match.';
      setError(msg);
      showToast({
        type: 'error',
        title: 'Analysis Failed',
        message: msg,
      });
    } finally {
      setIsLoading(false);
    }
  };

  const loadSampleJd = () => {
    setJobTitle('Backend Java Engineer');
    setCompanyName('FinTech Solutions Inc.');
    setJobDescription(`We are seeking a Backend Java Engineer with 3+ years of experience to design, build, and optimize scalable microservices.

Key Responsibilities:
- Architect and maintain enterprise REST APIs using Java and Spring Boot.
- Implement reliable data persistence models using PostgreSQL and Hibernate/JPA.
- Containerize microservices using Docker and orchestrate pipelines via CI/CD (GitHub Actions).
- Write high-coverage unit and integration tests using JUnit and Mockito.
- Optimize SQL queries and manage distributed caching with Redis.

Required Qualifications:
- 3+ years of professional experience in Java and Spring Boot.
- Solid understanding of relational databases (PostgreSQL/MySQL) and query indexing.
- Familiarity with containerization (Docker) and Git version control.
- Strong foundation in RESTful API design, microservices, and automated testing.`);
    setError(null);
  };

  const clearForm = () => {
    setJobDescription('');
    setJobTitle('');
    setCompanyName('');
    setError(null);
  };

  const getScoreColor = (score: number) => {
    if (score >= 75) {
      return {
        badge: 'bg-emerald-50 text-emerald-700 border-emerald-200',
        ring: 'text-emerald-600',
        bar: 'bg-emerald-600',
        bg: 'bg-emerald-500',
      };
    }
    if (score >= 50) {
      return {
        badge: 'bg-amber-50 text-amber-700 border-amber-200',
        ring: 'text-amber-600',
        bar: 'bg-amber-500',
        bg: 'bg-amber-500',
      };
    }
    return {
      badge: 'bg-rose-50 text-rose-700 border-rose-200',
      ring: 'text-rose-600',
      bar: 'bg-rose-500',
      bg: 'bg-rose-500',
    };
  };

  const charCount = jobDescription.length;
  const isOverLimit = charCount > 6000;
  const isNearLimit = charCount > 5500 && !isOverLimit;

  return (
    <div className="space-y-8 pb-12">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-indigo-700 via-indigo-800 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-xl shadow-indigo-950/10 relative overflow-hidden">
        <div className="absolute right-0 top-0 translate-x-8 -translate-y-8 w-64 h-64 bg-indigo-500/10 rounded-full blur-2xl pointer-events-none" />
        <div className="relative z-10 max-w-3xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 backdrop-blur-md border border-white/15 text-indigo-200 text-xs font-semibold uppercase tracking-wider mb-3">
            <Sparkles className="w-3.5 h-3.5 text-amber-300" />
            Plain Java Extraction + AI Synthesis
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Job Match Analyzer
          </h1>
          <p className="mt-2 text-indigo-100/90 text-sm sm:text-base leading-relaxed">
            Paste any Job Description up to 6,000 characters. Our deterministic Java matcher extracts required skills and experience from the JD, compares against your verified profile, and synthesizes 3 targeted tweaks for this exact role.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Input Form (5 cols on lg) */}
        <div className="lg:col-span-5 space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Briefcase className="w-4 h-4 text-indigo-600" />
                Target Job Details
              </h2>
              <button
                type="button"
                onClick={loadSampleJd}
                className="text-xs font-medium text-indigo-600 hover:text-indigo-700 hover:underline"
              >
                Load Sample JD
              </button>
            </div>

            {error && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleAnalyze} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Job Title <span className="text-slate-400 font-normal">(Optional)</span>
                  </label>
                  <input
                    type="text"
                    value={jobTitle}
                    onChange={(e) => setJobTitle(e.target.value)}
                    placeholder="e.g. Backend Engineer"
                    className="w-full px-3 py-2 text-sm rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition placeholder:text-slate-400"
                    maxLength={255}
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Company Name <span className="text-slate-400 font-normal">(Optional)</span>
                  </label>
                  <input
                    type="text"
                    value={companyName}
                    onChange={(e) => setCompanyName(e.target.value)}
                    placeholder="e.g. Acme Corp"
                    className="w-full px-3 py-2 text-sm rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition placeholder:text-slate-400"
                    maxLength={255}
                  />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-xs font-semibold text-slate-700">
                    Job Description <span className="text-rose-500">*</span>
                  </label>
                  <span
                    className={`text-xs font-mono transition ${
                      isOverLimit
                        ? 'text-rose-600 font-bold'
                        : isNearLimit
                        ? 'text-amber-600 font-semibold'
                        : 'text-slate-400'
                    }`}
                  >
                    {charCount.toLocaleString()} / 6,000 chars
                  </span>
                </div>
                <textarea
                  value={jobDescription}
                  onChange={(e) => setJobDescription(e.target.value)}
                  placeholder="Paste the full job description here (responsibilities, technical stack, required experience)..."
                  rows={9}
                  className={`w-full px-3.5 py-2.5 text-sm rounded-xl border transition focus:outline-none focus:ring-2 focus:border-transparent placeholder:text-slate-400 ${
                    isOverLimit
                      ? 'border-rose-400 focus:ring-rose-500 bg-rose-50/20'
                      : isNearLimit
                      ? 'border-amber-400 focus:ring-amber-500'
                      : 'border-slate-200 focus:ring-indigo-500'
                  }`}
                  maxLength={6000}
                />
              </div>

              <div className="flex items-center gap-3 pt-1">
                <button
                  type="submit"
                  disabled={isLoading || isOverLimit}
                  className="flex-1 inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Analyzing Job Match...</span>
                    </>
                  ) : (
                    <>
                      <Target className="w-4 h-4" />
                      <span>Analyze Job Match</span>
                    </>
                  )}
                </button>
                {jobDescription && (
                  <button
                    type="button"
                    onClick={clearForm}
                    disabled={isLoading}
                    className="px-3 py-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 text-xs font-medium transition cursor-pointer"
                  >
                    Clear
                  </button>
                )}
              </div>
            </form>
          </div>

          {/* History Card */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <History className="w-4 h-4 text-slate-500" />
                Previous Job Matches ({history.length})
              </h2>
            </div>

            {isLoadingHistory ? (
              <div className="py-6 text-center text-slate-400 text-xs flex items-center justify-center gap-2">
                <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />
                Loading history...
              </div>
            ) : history.length === 0 ? (
              <p className="text-xs text-slate-400 py-3 text-center">
                No past job match reports yet. Paste a JD above to run your first match.
              </p>
            ) : (
              <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                {history.map((rep) => {
                  const scoreColors = getScoreColor(rep.matchPercentage);
                  const isSelected = currentReport?.id === rep.id;
                  return (
                    <button
                      key={rep.id}
                      type="button"
                      onClick={() => setCurrentReport(rep)}
                      className={`w-full text-left p-3 rounded-xl border transition flex items-center justify-between gap-3 cursor-pointer ${
                        isSelected
                          ? 'border-indigo-400 bg-indigo-50/50 shadow-xs'
                          : 'border-slate-100 hover:border-slate-300 hover:bg-slate-50'
                      }`}
                    >
                      <div className="min-w-0 flex-1">
                        <div className="text-xs font-semibold text-slate-900 truncate">
                          {rep.jobTitle || 'Software Engineer'}
                        </div>
                        <div className="text-[11px] text-slate-500 truncate flex items-center gap-1.5 mt-0.5">
                          {rep.companyName && (
                            <>
                              <Building className="w-3 h-3 shrink-0" />
                              <span>{rep.companyName}</span>
                              <span>•</span>
                            </>
                          )}
                          <span>{new Date(rep.createdAt).toLocaleDateString()}</span>
                        </div>
                      </div>
                      <span
                        className={`text-xs font-bold px-2 py-0.5 rounded-lg border shrink-0 ${scoreColors.badge}`}
                      >
                        {rep.matchPercentage.toFixed(0)}%
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Results Dashboard (7 cols on lg) */}
        <div className="lg:col-span-7 space-y-6">
          {!currentReport && !isLoading && (
            <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto">
                <FileText className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">No Job Match Report Selected</h3>
              <p className="text-xs text-slate-500 max-w-sm mx-auto">
                Paste a job description on the left or click "Load Sample JD" to evaluate your skill alignment and get targeted profile tweaks.
              </p>
            </div>
          )}

          {isLoading && (
            <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center space-y-4 shadow-xs">
              <Loader2 className="w-8 h-8 text-indigo-600 animate-spin mx-auto" />
              <div>
                <h3 className="text-sm font-bold text-slate-900">Evaluating Job Match...</h3>
                <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
                  Running deterministic keyword extraction against skill catalog, checking GitHub verification status, and generating 3 targeted tweaks.
                </p>
              </div>
            </div>
          )}

          {currentReport && !isLoading && (
            <div className="space-y-6">
              {/* Score & Overview Header Card */}
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
                  <div>
                    <h2 className="text-lg font-bold text-slate-900">
                      {currentReport.jobTitle || 'Software Engineer Match'}
                    </h2>
                    <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 mt-1">
                      {currentReport.companyName && (
                        <span className="flex items-center gap-1 font-medium text-slate-700">
                          <Building className="w-3.5 h-3.5 text-slate-400" />
                          {currentReport.companyName}
                        </span>
                      )}
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        Required: {currentReport.extractedExperience || 'Not specified'}
                      </span>
                      <span>
                        Generated {new Date(currentReport.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </div>

                  {/* Match Percentage Pill */}
                  <div className="flex items-center gap-3 self-start sm:self-center">
                    <div
                      className={`px-4 py-2 rounded-2xl border text-center ${
                        getScoreColor(currentReport.matchPercentage).badge
                      }`}
                    >
                      <div className="text-2xl font-black tracking-tight leading-none">
                        {currentReport.matchPercentage.toFixed(1)}%
                      </div>
                      <div className="text-[10px] font-semibold uppercase tracking-wider mt-0.5">
                        Match Score
                      </div>
                    </div>
                  </div>
                </div>

                {/* AI Fit Summary */}
                <div className="bg-indigo-50/60 border border-indigo-100 rounded-xl p-4 flex items-start gap-3">
                  <Sparkles className="w-5 h-5 text-indigo-600 shrink-0 mt-0.5" />
                  <div className="space-y-1">
                    <div className="text-xs font-bold text-indigo-950 uppercase tracking-wide">
                      AI Fit Summary
                    </div>
                    <p className="text-xs sm:text-sm text-indigo-900/90 leading-relaxed">
                      {currentReport.fitSummary}
                    </p>
                  </div>
                </div>
              </div>

              {/* 3 Profile Tweaks for THIS Job */}
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Lightbulb className="w-4 h-4 text-amber-500" />
                    3 Targeted Profile Tweaks for THIS Job
                  </h3>
                  <span className="text-[11px] font-semibold text-indigo-600 bg-indigo-50 border border-indigo-100 px-2.5 py-0.5 rounded-full">
                    Tailored Advice
                  </span>
                </div>

                <div className="space-y-3">
                  {currentReport.profileTweaks.map((tweak, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 bg-slate-50/70 border border-slate-200/70 rounded-xl flex items-start gap-3"
                    >
                      <span className="w-6 h-6 rounded-lg bg-indigo-600 text-white font-bold text-xs flex items-center justify-center shrink-0 mt-0.5 shadow-xs">
                        {idx + 1}
                      </span>
                      <p className="text-xs sm:text-sm text-slate-700 leading-relaxed font-normal">
                        {tweak}
                      </p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Matched & Missing Skills Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Matched Skills */}
                <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-emerald-900 flex items-center gap-2">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                      Matched Skills ({currentReport.matchedSkills.length})
                    </h3>
                  </div>

                  {currentReport.matchedSkills.length === 0 ? (
                    <p className="text-xs text-slate-400 py-3 text-center">
                      No matching skills found between your profile and this job description.
                    </p>
                  ) : (
                    <div className="space-y-2.5">
                      {currentReport.matchedSkills.map((skill, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl border border-emerald-100 bg-emerald-50/30 flex items-center justify-between gap-2"
                        >
                          <div>
                            <span className="text-xs font-semibold text-slate-900 block">
                              {skill.skillName}
                            </span>
                            <span className="text-[10px] text-slate-500">
                              Claimed: {skill.claimedLevel}
                            </span>
                          </div>
                          <div>
                            {skill.verificationStatus === 'VERIFIED' ? (
                              <span className="inline-flex items-center gap-1 text-[10px] font-bold text-emerald-700 bg-emerald-100/80 border border-emerald-200 px-2 py-0.5 rounded-md">
                                <ShieldCheck className="w-3 h-3 text-emerald-600" />
                                Verified
                              </span>
                            ) : skill.verificationStatus === 'PARTIAL' ? (
                              <span className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-100/80 border border-amber-200 px-2 py-0.5 rounded-md">
                                Partial
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-[10px] font-medium text-slate-600 bg-slate-100 border border-slate-200 px-2 py-0.5 rounded-md">
                                Unverified
                              </span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Missing Skills */}
                <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 text-rose-500" />
                      Missing Skills ({currentReport.missingSkills.length})
                    </h3>
                  </div>

                  {currentReport.missingSkills.length === 0 ? (
                    <p className="text-xs text-emerald-600 font-medium py-3 text-center bg-emerald-50/50 rounded-xl border border-emerald-100">
                      Great job! Your profile covers all identified technical requirements.
                    </p>
                  ) : (
                    <div className="space-y-2.5">
                      {currentReport.missingSkills.map((skill, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl border border-slate-200 bg-slate-50/50 flex items-center justify-between gap-2"
                        >
                          <span className="text-xs font-semibold text-slate-800">
                            {skill.skillName}
                          </span>
                          <span className="text-[10px] font-medium text-slate-500 bg-white border border-slate-200 px-2 py-0.5 rounded-md">
                            {skill.category || 'Technical'}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Interview Topics to Prepare */}
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
                <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <Target className="w-4 h-4 text-indigo-600" />
                  Interview Topics to Prepare
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {currentReport.interviewTopics.map((topic, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded-xl border border-slate-100 bg-slate-50/70 text-xs text-slate-700 flex items-start gap-2.5"
                    >
                      <ArrowRight className="w-3.5 h-3.5 text-indigo-500 shrink-0 mt-0.5" />
                      <span className="leading-relaxed">{topic}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
