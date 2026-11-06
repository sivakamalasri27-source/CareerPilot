import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { suggestionApi } from '../api/suggestionApi';
import { SuggestionResponse, SuggestionStatus } from '../types/suggestion';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import { Modal } from '../components/common/Modal';
import {
  Inbox,
  CheckCircle2,
  XCircle,
  Edit3,
  Bot,
  RotateCcw,
  AlertTriangle,
  HelpCircle,
  Sparkles,
  Plus,
  Trash2,
  Briefcase,
  GraduationCap,
  Code2,
} from 'lucide-react';
import { ApiError } from '../types/common';

interface ParsedProjectData {
  title: string;
  description?: string;
  technologies?: string;
  role?: string;
  bullets?: string[];
}

interface ParsedEducationData {
  institution: string;
  degree: string;
  fieldOfStudy?: string;
  gradeOrGpa?: string;
  activities?: string;
}

interface ParsedSkillData {
  name: string;
  proficiency?: string;
}

const tryParseProjectJson = (text: string): ParsedProjectData | null => {
  if (!text) return null;
  const trimmed = text.trim();
  if (!trimmed.startsWith('{')) return null;
  try {
    const parsed = JSON.parse(trimmed);
    if (parsed && typeof parsed === 'object' && (parsed.title || parsed.bullets || parsed.technologies)) {
      return {
        title: parsed.title || '',
        description: parsed.description || '',
        technologies: parsed.technologies || '',
        role: parsed.role || '',
        bullets: Array.isArray(parsed.bullets) ? parsed.bullets : [],
      };
    }
  } catch {
    // not valid JSON
  }
  return null;
};

const tryParseEducationJson = (text: string): ParsedEducationData | null => {
  if (!text) return null;
  const trimmed = text.trim();
  if (!trimmed.startsWith('{')) return null;
  try {
    const parsed = JSON.parse(trimmed);
    if (parsed && typeof parsed === 'object' && (parsed.institution || parsed.degree)) {
      return {
        institution: parsed.institution || '',
        degree: parsed.degree || '',
        fieldOfStudy: parsed.fieldOfStudy || '',
        gradeOrGpa: parsed.gradeOrGpa || '',
        activities: parsed.activities || '',
      };
    }
  } catch {
    // not valid JSON
  }
  return null;
};

const parseSkillText = (text: string): ParsedSkillData | null => {
  if (!text) return null;
  const trimmed = text.trim();
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed);
      if (parsed && typeof parsed === 'object' && parsed.name) {
        return { name: parsed.name, proficiency: parsed.proficiency || 'INTERMEDIATE' };
      }
    } catch {
      // ignore
    }
  }
  const match = trimmed.match(/^(.+?)\s*\((BEGINNER|INTERMEDIATE|ADVANCED|EXPERT)\)$/i);
  if (match) {
    return { name: match[1].trim(), proficiency: match[2].toUpperCase() };
  }
  return null;
};

export const SuggestionsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [statusFilter, setStatusFilter] = useState<SuggestionStatus | 'ALL'>('PENDING');
  const [editingSuggestion, setEditingSuggestion] = useState<SuggestionResponse | null>(null);
  const [staleError, setStaleError] = useState<{ id: number; message: string } | null>(null);

  // Form State for Structured "Edit & Approve"
  const [formType, setFormType] = useState<'PROJECT' | 'EDUCATION' | 'SKILL' | 'TEXT'>('TEXT');
  const [projTitle, setProjTitle] = useState('');
  const [projRole, setProjRole] = useState('');
  const [projTech, setProjTech] = useState('');
  const [projDesc, setProjDesc] = useState('');
  const [projBullets, setProjBullets] = useState<string[]>(['']);

  const [eduInst, setEduInst] = useState('');
  const [eduDegree, setEduDegree] = useState('');
  const [eduField, setEduField] = useState('');
  const [eduGrade, setEduGrade] = useState('');

  const [skillName, setSkillName] = useState('');
  const [skillLevel, setSkillLevel] = useState('INTERMEDIATE');

  const [plainText, setPlainText] = useState('');

  // Queries
  const { data: suggestionsPage, isLoading, refetch } = useQuery({
    queryKey: ['suggestions', statusFilter],
    queryFn: () =>
      suggestionApi.getSuggestions(
        statusFilter === 'ALL' ? undefined : statusFilter,
        0,
        50
      ),
  });

  // Approve Mutation
  const approveMutation = useMutation({
    mutationFn: ({ id, editedText }: { id: number; editedText?: string }) =>
      suggestionApi.approveSuggestion(id, editedText ? { editedText } : undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suggestions'] });
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      queryClient.invalidateQueries({ queryKey: ['profile', 'projects'] });
      queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
      queryClient.invalidateQueries({ queryKey: ['profile', 'education'] });
      setEditingSuggestion(null);
      setStaleError(null);
      showToast({
        type: 'success',
        title: 'Suggestion Approved',
        message: 'Your profile and records have been updated with this improvement.',
      });
    },
    onError: (err: ApiError, variables) => {
      if (err.status === 409) {
        setStaleError({
          id: variables.id,
          message: err.message || 'This suggestion is stale or already decided.',
        });
        showToast({
          type: 'warning',
          title: 'Conflict (409)',
          message: 'Target data changed since this suggestion was created. Please refresh.',
        });
      } else {
        showToast({
          type: 'error',
          title: 'Approval Failed',
          message: err.message || 'Could not approve suggestion.',
        });
      }
    },
  });

  // Reject Mutation
  const rejectMutation = useMutation({
    mutationFn: (id: number) => suggestionApi.rejectSuggestion(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suggestions'] });
      setStaleError(null);
      showToast({
        type: 'info',
        title: 'Suggestion Rejected',
        message: 'The suggested changes were discarded.',
      });
    },
    onError: (err: ApiError, id) => {
      if (err.status === 409) {
        setStaleError({
          id,
          message: err.message || 'Suggestion already decided.',
        });
      } else {
        showToast({
          type: 'error',
          title: 'Reject Failed',
          message: err.message || 'Could not reject suggestion.',
        });
      }
    },
  });

  const openEditModal = (sug: SuggestionResponse) => {
    const isProjectSection = sug.targetSection === 'PROJECT' || sug.targetSection === 'PROJECT_BULLETS';
    const projectParsed = tryParseProjectJson(sug.suggestedText);

    if (isProjectSection && (projectParsed || sug.targetId == null)) {
      setFormType('PROJECT');
      setProjTitle(projectParsed?.title || '');
      setProjRole(projectParsed?.role || '');
      setProjTech(projectParsed?.technologies || '');
      setProjDesc(projectParsed?.description || '');
      const bullets = (projectParsed?.bullets && projectParsed.bullets.length > 0)
        ? projectParsed.bullets
        : (sug.suggestedBullets && sug.suggestedBullets.length > 0)
        ? sug.suggestedBullets
        : [''];
      setProjBullets([...bullets]);
    } else if (sug.targetSection === 'EDUCATION' || tryParseEducationJson(sug.suggestedText)) {
      const eduParsed = tryParseEducationJson(sug.suggestedText);
      setFormType('EDUCATION');
      setEduInst(eduParsed?.institution || '');
      setEduDegree(eduParsed?.degree || '');
      setEduField(eduParsed?.fieldOfStudy || '');
      setEduGrade(eduParsed?.gradeOrGpa || '');
    } else if (sug.targetSection === 'SKILL' || parseSkillText(sug.suggestedText)) {
      const skillParsed = parseSkillText(sug.suggestedText);
      setFormType('SKILL');
      setSkillName(skillParsed?.name || sug.suggestedText);
      setSkillLevel(skillParsed?.proficiency || 'INTERMEDIATE');
    } else {
      setFormType('TEXT');
      setPlainText(sug.suggestedText);
    }

    setEditingSuggestion(sug);
  };

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSuggestion) return;

    let serializedPayload = '';

    if (formType === 'PROJECT') {
      const cleanBullets = projBullets.map((b) => b.trim()).filter((b) => b.length > 0);
      if (editingSuggestion.targetId != null) {
        // Updating existing project bullets
        serializedPayload = JSON.stringify(cleanBullets);
      } else {
        // Creating new project
        serializedPayload = JSON.stringify({
          title: projTitle.trim(),
          role: projRole.trim(),
          technologies: projTech.trim(),
          description: projDesc.trim(),
          bullets: cleanBullets,
        });
      }
    } else if (formType === 'EDUCATION') {
      serializedPayload = JSON.stringify({
        institution: eduInst.trim(),
        degree: eduDegree.trim(),
        fieldOfStudy: eduField.trim(),
        gradeOrGpa: eduGrade.trim(),
      });
    } else if (formType === 'SKILL') {
      serializedPayload = `${skillName.trim()} (${skillLevel.toUpperCase()})`;
    } else {
      serializedPayload = plainText.trim();
    }

    approveMutation.mutate({
      id: editingSuggestion.id,
      editedText: serializedPayload,
    });
  };

  const suggestions = suggestionsPage?.content || [];

  const getNewBadgeLabel = (targetSection: string): string => {
    const sec = targetSection.toUpperCase();
    if (sec === 'PROJECT' || sec === 'PROJECT_BULLETS') return 'New Project';
    if (sec === 'SKILL') return 'New Skill';
    if (sec === 'EDUCATION') return 'New Education';
    if (sec === 'HEADLINE') return 'New Headline';
    if (sec === 'ABOUT') return 'New About Bio';
    return 'New Item';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <Inbox className="w-7 h-7 text-indigo-600" />
            Suggestions Inbox
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Review, edit, and approve industry-grade translations and resume imports before they touch your profile.
          </p>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-2 bg-slate-100 p-1 rounded-xl">
          {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as const).map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => {
                setStatusFilter(st);
                setStaleError(null);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                statusFilter === st
                  ? 'bg-white text-indigo-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {st}
            </button>
          ))}
        </div>
      </div>

      {/* 409 Global Conflict Notification Banner */}
      {staleError && (
        <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 flex items-start justify-between gap-4 text-amber-900 animate-slide-in">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-bold">Stale or Conflicted Suggestion (409)</p>
              <p className="text-xs text-amber-800 mt-0.5">{staleError.message}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setStaleError(null);
              refetch();
            }}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-white border border-amber-300 text-amber-900 hover:bg-amber-100/60 shadow-xs transition"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Refresh Suggestions
          </button>
        </div>
      )}

      {/* Content */}
      {isLoading ? (
        <CardSkeleton count={3} />
      ) : suggestions.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title={`No ${statusFilter === 'ALL' ? '' : statusFilter.toLowerCase()} suggestions`}
          description="Experience translator suggestions and parsed resume bullets will appear here for your review."
        />
      ) : (
        <div className="space-y-6">
          {suggestions.map((sug) => {
            const isPending = sug.status === 'PENDING';
            const isApproved = sug.status === 'APPROVED';
            const isRejected = sug.status === 'REJECTED';
            const isNewItem = !sug.originalText || sug.originalText.trim() === '';

            // Attempt structured parsing
            const projectData = tryParseProjectJson(sug.suggestedText);
            const eduData = tryParseEducationJson(sug.suggestedText);
            const skillData = parseSkillText(sug.suggestedText);

            return (
              <div
                key={sug.id}
                className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden"
                data-testid={`suggestion-card-${sug.id}`}
              >
                {/* Suggestion Card Header */}
                <div className="p-4 sm:px-6 bg-slate-50/70 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="p-1.5 rounded-lg bg-indigo-100 text-indigo-700">
                      <Bot className="w-4 h-4" />
                    </span>
                    <span className="text-xs font-bold text-slate-800">{sug.agentName}</span>
                    <span className="text-slate-300">•</span>
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Target: {sug.targetSection}
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-[11px] font-extrabold uppercase border ${
                        isApproved
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : isRejected
                          ? 'bg-rose-50 text-rose-700 border-rose-200'
                          : 'bg-amber-50 text-amber-700 border-amber-200'
                      }`}
                    >
                      {sug.status}
                    </span>
                    <span className="text-xs text-slate-400">
                      {new Date(sug.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                {/* Side-by-Side Comparison Grid */}
                <div className="p-6 grid grid-cols-1 md:grid-cols-2 gap-6">
                  {/* Left: Original Text or New Item Badge */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                        {isNewItem ? 'Origin' : 'Original Text'}
                      </span>
                    </div>

                    {isNewItem ? (
                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 flex flex-col justify-center gap-2 min-h-[110px]">
                        <div className="flex items-center gap-2">
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
                            <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                            {getNewBadgeLabel(sug.targetSection)}
                          </span>
                        </div>
                        <p className="text-xs text-slate-500 leading-relaxed">
                          New item extracted from your uploaded resume. Approving this suggestion will create a new record in your profile.
                        </p>
                      </div>
                    ) : (
                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-700 leading-relaxed font-mono whitespace-pre-wrap min-h-[110px]">
                        {sug.originalText}
                      </div>
                    )}
                  </div>

                  {/* Right: Suggested Enhancement (Readable Card) */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-indigo-600 uppercase tracking-wider flex items-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5" />
                        Suggested Enhancement
                      </span>
                    </div>

                    {projectData ? (
                      <div className="p-4 rounded-xl bg-indigo-50/40 border border-indigo-200/80 space-y-3 min-h-[110px]">
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <h4 className="font-bold text-slate-900 text-base flex items-center gap-2">
                              <Briefcase className="w-4 h-4 text-indigo-600" />
                              {projectData.title}
                            </h4>
                            {projectData.role && (
                              <span className="inline-block text-xs font-semibold text-indigo-600 mt-0.5">
                                Role: {projectData.role}
                              </span>
                            )}
                          </div>
                        </div>

                        {projectData.description && (
                          <p className="text-xs text-slate-700 leading-relaxed">{projectData.description}</p>
                        )}

                        {projectData.technologies && (
                          <div className="flex flex-wrap gap-1.5 pt-1">
                            {projectData.technologies.split(',').map((tech, i) => (
                              <span
                                key={i}
                                className="px-2 py-0.5 bg-white border border-slate-200 text-slate-700 rounded-md text-[11px] font-medium"
                              >
                                {tech.trim()}
                              </span>
                            ))}
                          </div>
                        )}

                        {projectData.bullets && projectData.bullets.length > 0 && (
                          <ul className="list-disc list-inside space-y-1 text-xs text-slate-800 pt-2 border-t border-indigo-100">
                            {projectData.bullets.map((b, idx) => (
                              <li key={idx} className="leading-relaxed">{b}</li>
                            ))}
                          </ul>
                        )}
                      </div>
                    ) : eduData ? (
                      <div className="p-4 rounded-xl bg-indigo-50/40 border border-indigo-200/80 space-y-2 min-h-[110px]">
                        <h4 className="font-bold text-slate-900 text-base flex items-center gap-2">
                          <GraduationCap className="w-4 h-4 text-indigo-600" />
                          {eduData.degree}
                        </h4>
                        <p className="text-sm font-semibold text-indigo-600">{eduData.institution}</p>
                        {eduData.fieldOfStudy && (
                          <p className="text-xs text-slate-600">Field: {eduData.fieldOfStudy}</p>
                        )}
                        {eduData.gradeOrGpa && (
                          <p className="text-xs text-slate-600">Grade / GPA: {eduData.gradeOrGpa}</p>
                        )}
                      </div>
                    ) : skillData ? (
                      <div className="p-4 rounded-xl bg-indigo-50/40 border border-indigo-200/80 flex items-center justify-between min-h-[110px]">
                        <div>
                          <h4 className="font-bold text-slate-900 text-base flex items-center gap-2">
                            <Code2 className="w-4 h-4 text-indigo-600" />
                            {skillData.name}
                          </h4>
                          <span className="inline-block text-xs font-semibold px-2 py-0.5 mt-1.5 rounded-md bg-indigo-100 text-indigo-800">
                            Claimed Level: {skillData.proficiency}
                          </span>
                        </div>
                      </div>
                    ) : sug.suggestedBullets && sug.suggestedBullets.length > 0 ? (
                      <div className="p-4 rounded-xl bg-indigo-50/40 border border-indigo-200/80 text-sm text-slate-900 min-h-[110px]">
                        <ul className="list-disc list-inside space-y-1.5 text-xs text-slate-800">
                          {sug.suggestedBullets.map((b, idx) => (
                            <li key={idx} className="leading-relaxed">{b}</li>
                          ))}
                        </ul>
                      </div>
                    ) : (
                      <div className="p-4 rounded-xl bg-indigo-50/40 border border-indigo-200/80 text-sm text-slate-900 leading-relaxed whitespace-pre-wrap min-h-[110px]">
                        {sug.suggestedText}
                      </div>
                    )}
                  </div>
                </div>

                {/* Rationale */}
                <div className="px-6 pb-4">
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs text-slate-600">
                    <span className="font-semibold text-slate-800">Rationale: </span>
                    {sug.rationale}
                  </div>
                </div>

                {/* Clarifying Questions (if any) */}
                {sug.clarifyingQuestions && sug.clarifyingQuestions.length > 0 && (
                  <div className="px-6 pb-4">
                    <div className="p-4 rounded-xl bg-blue-50/60 border border-blue-200/80 space-y-2.5">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-blue-900">
                        <HelpCircle className="w-4 h-4 text-blue-600" />
                        Clarifying Questions (Missing Metrics / Context)
                      </div>
                      <p className="text-xs text-blue-700">
                        The AI refused to fabricate metrics. You can answer these prompts and use "Edit & Approve":
                      </p>
                      <div className="space-y-2 mt-2">
                        {sug.clarifyingQuestions.map((q, qIdx) => (
                          <div key={qIdx} className="space-y-1">
                            <label className="text-xs font-medium text-slate-700">{q}</label>
                            <input
                              type="text"
                              placeholder="e.g. 500+ active users, 25% query latency reduction"
                              className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-300 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
                            />
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                {/* Actions Footer */}
                {isPending && (
                  <div className="p-4 sm:px-6 bg-slate-50/50 border-t border-slate-100 flex flex-wrap items-center justify-end gap-3">
                    <button
                      type="button"
                      disabled={rejectMutation.isPending}
                      onClick={() => rejectMutation.mutate(sug.id)}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 transition"
                      aria-label="Reject suggestion"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      Reject
                    </button>
                    <button
                      type="button"
                      onClick={() => openEditModal(sug)}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-300 shadow-2xs transition"
                      aria-label="Edit and approve suggestion"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      Edit & Approve
                    </button>
                    <button
                      type="button"
                      disabled={approveMutation.isPending}
                      onClick={() => approveMutation.mutate({ id: sug.id })}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-xs transition"
                      aria-label="Approve suggestion"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Approve
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Edit Before Approve Modal with Structured Form Fields */}
      <Modal
        isOpen={!!editingSuggestion}
        onClose={() => setEditingSuggestion(null)}
        title={
          formType === 'PROJECT'
            ? 'Edit Project Before Approving'
            : formType === 'EDUCATION'
            ? 'Edit Education Record'
            : formType === 'SKILL'
            ? 'Edit Skill'
            : 'Edit Text Before Approving'
        }
        maxWidth="2xl"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          {formType === 'PROJECT' ? (
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Project Title <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={255}
                  value={projTitle}
                  onChange={(e) => setProjTitle(e.target.value)}
                  placeholder="e.g. Microservices E-Commerce Platform"
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Role / Title
                  </label>
                  <input
                    type="text"
                    maxLength={100}
                    value={projRole}
                    onChange={(e) => setProjRole(e.target.value)}
                    placeholder="e.g. Backend Lead"
                    className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Technologies
                  </label>
                  <input
                    type="text"
                    maxLength={255}
                    value={projTech}
                    onChange={(e) => setProjTech(e.target.value)}
                    placeholder="e.g. Java, Spring Boot, PostgreSQL"
                    className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Description
                </label>
                <textarea
                  rows={2}
                  value={projDesc}
                  onChange={(e) => setProjDesc(e.target.value)}
                  placeholder="Brief overview of the project..."
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Accomplishment Bullets (STAR/XYZ format)
                  </label>
                  {projBullets.length < 8 && (
                    <button
                      type="button"
                      onClick={() => setProjBullets([...projBullets, ''])}
                      className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Add Bullet
                    </button>
                  )}
                </div>
                <div className="space-y-2">
                  {projBullets.map((bullet, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <input
                        type="text"
                        maxLength={300}
                        value={bullet}
                        onChange={(e) => {
                          const updated = [...projBullets];
                          updated[idx] = e.target.value;
                          setProjBullets(updated);
                        }}
                        placeholder={`Bullet #${idx + 1}: Action + Task + Tech + Result`}
                        className="flex-1 px-3.5 py-2 rounded-xl border border-slate-300 text-xs focus:ring-indigo-500 focus:border-indigo-500"
                      />
                      {projBullets.length > 1 && (
                        <button
                          type="button"
                          onClick={() => {
                            setProjBullets(projBullets.filter((_, i) => i !== idx));
                          }}
                          className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                          aria-label="Remove bullet"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : formType === 'EDUCATION' ? (
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Degree <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={255}
                  value={eduDegree}
                  onChange={(e) => setEduDegree(e.target.value)}
                  placeholder="e.g. B.Tech Computer Science"
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Institution <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={255}
                  value={eduInst}
                  onChange={(e) => setEduInst(e.target.value)}
                  placeholder="e.g. Anna University"
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Field of Study
                  </label>
                  <input
                    type="text"
                    maxLength={255}
                    value={eduField}
                    onChange={(e) => setEduField(e.target.value)}
                    placeholder="e.g. Computer Science and Engineering"
                    className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Grade / GPA
                  </label>
                  <input
                    type="text"
                    maxLength={50}
                    value={eduGrade}
                    onChange={(e) => setEduGrade(e.target.value)}
                    placeholder="e.g. 8.5 / 10 CGPA"
                    className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>
            </div>
          ) : formType === 'SKILL' ? (
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Skill Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={100}
                  value={skillName}
                  onChange={(e) => setSkillName(e.target.value)}
                  placeholder="e.g. Java"
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Proficiency Level
                </label>
                <select
                  value={skillLevel}
                  onChange={(e) => setSkillLevel(e.target.value)}
                  className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500 bg-white"
                >
                  <option value="BEGINNER">BEGINNER</option>
                  <option value="INTERMEDIATE">INTERMEDIATE</option>
                  <option value="ADVANCED">ADVANCED</option>
                  <option value="EXPERT">EXPERT</option>
                </select>
              </div>
            </div>
          ) : (
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Adjust Suggested Text
              </label>
              <textarea
                rows={5}
                required
                value={plainText}
                onChange={(e) => setPlainText(e.target.value)}
                className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>
          )}

          <div className="pt-4 border-t border-slate-100 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setEditingSuggestion(null)}
              className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={approveMutation.isPending}
              className="px-5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60"
            >
              Confirm & Apply
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
