import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { profileApi } from '../api/profileApi';
import {
  ProfileRequest,
  ProfileSkillResponse,
  ProjectResponse,
  EducationResponse,
  SkillProficiency,
} from '../types/profile';
import { useToast } from '../context/ToastContext';
import { SkillBadge } from '../components/common/SkillBadge';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { EmptyState } from '../components/common/EmptyState';
import { Modal } from '../components/common/Modal';
import {
  UserCheck,
  Save,
  Plus,
  Trash2,
  Edit2,
  Code2,
  Briefcase,
  GraduationCap,
  Github,
  Loader2,
  ExternalLink,
  CheckCircle2,
  AlertCircle,
  Sparkles,
} from 'lucide-react';
import { ApiError } from '../types/common';

export const extractGithubUsername = (raw: string): string => {
  if (!raw) return '';
  let cleaned = raw.trim();
  cleaned = cleaned.replace(/^(?:https?:\/\/)?(?:www\.)?github\.com\//i, '');
  cleaned = cleaned.replace(/^@/, '');
  const slashIdx = cleaned.indexOf('/');
  if (slashIdx !== -1) {
    cleaned = cleaned.substring(0, slashIdx);
  }
  return cleaned.trim();
};

export const isValidGithubUsername = (username: string): boolean => {
  if (!username) return true;
  return /^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$/.test(username);
};

export const countWords = (text: string): number => {
  if (!text) return 0;
  const trimmed = text.trim();
  if (!trimmed) return 0;
  return trimmed.split(/\s+/).filter(Boolean).length;
};

export const ProfileBuilderPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  // Active section tab: 'basics' | 'skills' | 'projects' | 'education'
  const [activeTab, setActiveTab] = useState<'basics' | 'skills' | 'projects' | 'education'>('basics');

  // Queries
  const { data: profile, isLoading: isProfileLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: profileApi.getProfile,
  });

  const { data: skillsPage, isLoading: isSkillsLoading } = useQuery({
    queryKey: ['profile', 'skills'],
    queryFn: () => profileApi.getSkills(0, 50),
  });

  const { data: projectsPage, isLoading: isProjectsLoading } = useQuery({
    queryKey: ['profile', 'projects'],
    queryFn: () => profileApi.getProjects(0, 50),
  });

  const { data: educationPage, isLoading: isEducationLoading } = useQuery({
    queryKey: ['profile', 'education'],
    queryFn: () => profileApi.getEducation(0, 50),
  });

  // Basics Form State
  const [headline, setHeadline] = useState<string>('');
  const [about, setAbout] = useState<string>('');
  const [githubUsername, setGithubUsername] = useState<string>('');
  const [targetRole, setTargetRole] = useState<string>('');
  const [phone, setPhone] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [linkedinUrl, setLinkedinUrl] = useState<string>('');
  const [portfolioUrl, setPortfolioUrl] = useState<string>('');
  const [isBasicsInitialized, setIsBasicsInitialized] = useState<boolean>(false);

  // Sync profile data into form state once loaded
  if (profile && !isBasicsInitialized) {
    setHeadline(profile.headline || '');
    setAbout(profile.about || '');
    setGithubUsername(profile.githubUsername || '');
    setTargetRole(profile.targetRole || '');
    setPhone(profile.phone || '');
    setLocation(profile.location || '');
    setLinkedinUrl(profile.linkedinUrl || '');
    setPortfolioUrl(profile.portfolioUrl || '');
    setIsBasicsInitialized(true);
  }

  // Update Profile Mutation
  const updateProfileMutation = useMutation({
    mutationFn: (data: ProfileRequest) => profileApi.updateProfile(data),
    onSuccess: (updated) => {
      queryClient.setQueryData(['profile'], updated);
      showToast({
        type: 'success',
        title: 'Profile Updated',
        message: 'Your personal information was saved successfully.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Save Failed',
        message: err.message || 'Could not update profile.',
      });
    },
  });

  // Skills Mutations & Modals
  const [isSkillModalOpen, setIsSkillModalOpen] = useState(false);
  const [editingSkill, setEditingSkill] = useState<ProfileSkillResponse | null>(null);
  const [skillName, setSkillName] = useState('');
  const [skillLevel, setSkillLevel] = useState<SkillProficiency>('INTERMEDIATE');

  const addSkillMutation = useMutation({
    mutationFn: profileApi.addSkill,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
      setIsSkillModalOpen(false);
      setSkillName('');
      showToast({
        type: 'success',
        title: 'Skill Added',
        message: 'Skill added to your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Failed to Add Skill',
        message: err.message || 'Could not add skill.',
      });
    },
  });

  const updateSkillMutation = useMutation({
    mutationFn: ({ id, claimedLevel }: { id: number; claimedLevel: SkillProficiency }) =>
      profileApi.updateSkill(id, { claimedLevel }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
      setIsSkillModalOpen(false);
      setEditingSkill(null);
      showToast({
        type: 'success',
        title: 'Skill Updated',
        message: 'Skill proficiency updated successfully.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Update Failed',
        message: err.message || 'Failed to update skill proficiency.',
      });
    },
  });

  const deleteSkillMutation = useMutation({
    mutationFn: profileApi.deleteSkill,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
      showToast({
        type: 'success',
        title: 'Skill Removed',
        message: 'Skill was removed from your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Delete Failed',
        message: err.message || 'Failed to remove skill.',
      });
    },
  });

  // Projects Modal & State
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<ProjectResponse | null>(null);
  const [projectTitle, setProjectTitle] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [projectRole, setProjectRole] = useState('');
  const [projectTech, setProjectTech] = useState('');
  const [projectRepo, setProjectRepo] = useState('');
  const [projectLive, setProjectLive] = useState('');
  const [projectBullets, setProjectBullets] = useState<string[]>(['']);

  const openProjectModal = (proj?: ProjectResponse) => {
    if (proj) {
      setEditingProject(proj);
      setProjectTitle(proj.title);
      setProjectDescription(proj.description || '');
      setProjectRole(proj.role || '');
      setProjectTech(proj.technologies || '');
      setProjectRepo(proj.repoUrl || '');
      setProjectLive(proj.liveUrl || '');
      setProjectBullets(proj.bullets && proj.bullets.length > 0 ? [...proj.bullets] : ['']);
    } else {
      setEditingProject(null);
      setProjectTitle('');
      setProjectDescription('');
      setProjectRole('');
      setProjectTech('');
      setProjectRepo('');
      setProjectLive('');
      setProjectBullets(['']);
    }
    setIsProjectModalOpen(true);
  };

  const saveProjectMutation = useMutation({
    mutationFn: async () => {
      const cleanBullets = projectBullets.map((b) => b.trim()).filter((b) => b.length > 0);
      const payload = {
        title: projectTitle.trim(),
        description: projectDescription.trim(),
        role: projectRole.trim(),
        technologies: projectTech.trim(),
        repoUrl: projectRepo.trim(),
        liveUrl: projectLive.trim(),
        bullets: cleanBullets,
      };

      if (editingProject) {
        return profileApi.updateProject(editingProject.id, payload);
      } else {
        return profileApi.addProject(payload);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'projects'] });
      setIsProjectModalOpen(false);
      showToast({
        type: 'success',
        title: editingProject ? 'Project Updated' : 'Project Added',
        message: editingProject
          ? 'Project details were saved successfully.'
          : 'New project has been added to your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Failed to Save Project',
        message: err.message || 'Could not save project. Please verify the required fields.',
      });
    },
  });

  const deleteProjectMutation = useMutation({
    mutationFn: profileApi.deleteProject,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'projects'] });
      showToast({
        type: 'success',
        title: 'Project Deleted',
        message: 'Project was removed from your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Delete Failed',
        message: err.message || 'Could not delete project.',
      });
    },
  });

  // Education Modal & State
  const [isEducationModalOpen, setIsEducationModalOpen] = useState(false);
  const [editingEdu, setEditingEdu] = useState<EducationResponse | null>(null);
  const [eduInstitution, setEduInstitution] = useState('');
  const [eduDegree, setEduDegree] = useState('');
  const [eduField, setEduField] = useState('');
  const [eduGrade, setEduGrade] = useState('');
  const [eduActivities, setEduActivities] = useState('');

  const openEduModal = (edu?: EducationResponse) => {
    if (edu) {
      setEditingEdu(edu);
      setEduInstitution(edu.institution);
      setEduDegree(edu.degree);
      setEduField(edu.fieldOfStudy || '');
      setEduGrade(edu.gradeOrGpa || '');
      setEduActivities(edu.activities || '');
    } else {
      setEditingEdu(null);
      setEduInstitution('');
      setEduDegree('');
      setEduField('');
      setEduGrade('');
      setEduActivities('');
    }
    setIsEducationModalOpen(true);
  };

  const saveEduMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        institution: eduInstitution.trim(),
        degree: eduDegree.trim(),
        fieldOfStudy: eduField.trim(),
        gradeOrGpa: eduGrade.trim(),
        activities: eduActivities.trim(),
      };
      if (editingEdu) {
        return profileApi.updateEducation(editingEdu.id, payload);
      } else {
        return profileApi.addEducation(payload);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'education'] });
      setIsEducationModalOpen(false);
      showToast({
        type: 'success',
        title: editingEdu ? 'Education Updated' : 'Education Added',
        message: editingEdu
          ? 'Education record updated successfully.'
          : 'New education entry added to your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Failed to Save Education',
        message: err.message || 'Could not save education record. Please verify the required fields.',
      });
    },
  });

  const deleteEduMutation = useMutation({
    mutationFn: profileApi.deleteEducation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'education'] });
      showToast({
        type: 'success',
        title: 'Education Deleted',
        message: 'Education record was removed from your profile.',
      });
    },
    onError: (err: ApiError) => {
      showToast({
        type: 'error',
        title: 'Delete Failed',
        message: err.message || 'Could not delete education record.',
      });
    },
  });

  const handleGithubUsernameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    const extracted = extractGithubUsername(raw);
    setGithubUsername(extracted);
  };

  const handleSaveBasics = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanGithub = extractGithubUsername(githubUsername);
    if (cleanGithub && !isValidGithubUsername(cleanGithub)) {
      showToast({
        type: 'error',
        title: 'Validation Error',
        message: 'Enter only your GitHub username, for example octocat',
      });
      return;
    }

    updateProfileMutation.mutate({
      headline: headline.trim(),
      about: about.trim(),
      githubUsername: cleanGithub || undefined,
      targetRole: targetRole.trim() || undefined,
      phone: phone.trim() || undefined,
      location: location.trim() || undefined,
      linkedinUrl: linkedinUrl.trim() || undefined,
      portfolioUrl: portfolioUrl.trim() || undefined,
      version: profile?.version,
    });
  };

  // Profile Completeness & Quality Calculation
  const currentHeadline = (headline || profile?.headline || '').trim();
  const currentAbout = (about || profile?.about || '').trim();
  const currentTargetRole = (targetRole || profile?.targetRole || '').trim();
  const currentGithub = (githubUsername || profile?.githubUsername || '').trim();

  const hasHeadline = currentHeadline.length > 0;
  const hasTargetRole = currentTargetRole.length > 0;
  const hasAbout = currentAbout.length > 0;
  const hasContact = Boolean(
    profile?.location || location || profile?.linkedinUrl || linkedinUrl || profile?.phone || phone || profile?.portfolioUrl || portfolioUrl
  );
  const hasGithub = currentGithub.length > 0;

  const skillsList = skillsPage?.content || [];
  const skillsCount = skillsPage?.totalElements || skillsList.length;
  const hasSkills = skillsCount > 0;

  const projectsList = projectsPage?.content || [];
  const projectsCount = projectsPage?.totalElements || projectsList.length;
  const hasProjects = projectsCount > 0;

  const educationList = educationPage?.content || [];
  const educationCount = educationPage?.totalElements || educationList.length;
  const hasEducation = educationCount > 0;

  // Quality Rules Checks:
  // 1. Headline under 40 chars
  const isHeadlineQualityPassed = currentHeadline.length >= 40;
  // 2. About under 100 chars
  const isAboutQualityPassed = currentAbout.length >= 100;
  // 3. Project with no bullets (every project must have at least one non-empty bullet)
  const hasProjectWithNoBullets = projectsList.some(
    (p) => !p.bullets || p.bullets.length === 0 || p.bullets.every((b) => !b || !b.trim())
  );
  const isProjectsQualityPassed = hasProjects && !hasProjectWithNoBullets;
  // 4. Skills under 5
  const isSkillsQualityPassed = skillsCount >= 5;

  interface CompletenessSection {
    id: string;
    title: string;
    tab: 'basics' | 'skills' | 'projects' | 'education';
    isFilled: boolean;
    qualityPassed: boolean;
    status: 'complete' | 'needs-improvement' | 'empty';
    completed: boolean;
    detail: string;
    tip?: string;
  }

  const completenessSections: CompletenessSection[] = [
    {
      id: 'headline',
      title: 'Headline & Role',
      tab: 'basics',
      isFilled: hasHeadline && hasTargetRole,
      qualityPassed: isHeadlineQualityPassed,
      status: !(hasHeadline && hasTargetRole)
        ? 'empty'
        : !isHeadlineQualityPassed
        ? 'needs-improvement'
        : 'complete',
      completed: hasHeadline && hasTargetRole && isHeadlineQualityPassed,
      detail: hasHeadline && hasTargetRole ? 'Complete' : !hasHeadline ? 'Missing Headline' : 'Missing Target Role',
      tip: 'Headline under 40 chars — add target role & key tech',
    },
    {
      id: 'about',
      title: 'About / Bio',
      tab: 'basics',
      isFilled: hasAbout,
      qualityPassed: isAboutQualityPassed,
      status: !hasAbout
        ? 'empty'
        : !isAboutQualityPassed
        ? 'needs-improvement'
        : 'complete',
      completed: hasAbout && isAboutQualityPassed,
      detail: hasAbout ? 'Complete' : 'Empty Summary',
      tip: 'About under 100 chars — describe passions & tech stack',
    },
    {
      id: 'contact',
      title: 'Contact Details',
      tab: 'basics',
      isFilled: hasContact,
      qualityPassed: true,
      status: hasContact ? 'complete' : 'empty',
      completed: hasContact,
      detail: hasContact ? 'Complete' : 'Phone / Location empty',
    },
    {
      id: 'github',
      title: 'GitHub Profile',
      tab: 'basics',
      isFilled: hasGithub,
      qualityPassed: true,
      status: hasGithub ? 'complete' : 'empty',
      completed: hasGithub,
      detail: hasGithub ? `@${currentGithub}` : 'Not connected',
    },
    {
      id: 'skills',
      title: 'Technical Skills',
      tab: 'skills',
      isFilled: hasSkills,
      qualityPassed: isSkillsQualityPassed,
      status: !hasSkills
        ? 'empty'
        : !isSkillsQualityPassed
        ? 'needs-improvement'
        : 'complete',
      completed: hasSkills && isSkillsQualityPassed,
      detail: hasSkills ? `${skillsCount} added` : '0 skills listed',
      tip: `Add at least 5 skills (currently ${skillsCount} added)`,
    },
    {
      id: 'projects',
      title: 'Projects & Work',
      tab: 'projects',
      isFilled: hasProjects,
      qualityPassed: isProjectsQualityPassed,
      status: !hasProjects
        ? 'empty'
        : !isProjectsQualityPassed
        ? 'needs-improvement'
        : 'complete',
      completed: hasProjects && isProjectsQualityPassed,
      detail: hasProjects ? `${projectsCount} added` : '0 projects listed',
      tip: 'Project has no bullets — add achievement bullet points',
    },
    {
      id: 'education',
      title: 'Education',
      tab: 'education',
      isFilled: hasEducation,
      qualityPassed: true,
      status: hasEducation ? 'complete' : 'empty',
      completed: hasEducation,
      detail: hasEducation ? `${educationCount} degree${educationCount > 1 ? 's' : ''}` : 'No degree listed',
    },
  ];

  const completedCount = completenessSections.filter((s) => s.status === 'complete').length;
  const needsImprovementCount = completenessSections.filter((s) => s.status === 'needs-improvement').length;
  const emptyCount = completenessSections.filter((s) => s.status === 'empty').length;
  const completenessPercent = Math.round((completedCount / completenessSections.length) * 100);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <UserCheck className="w-7 h-7 text-indigo-600" />
            Candidate Profile Builder
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Keep your skills, projects, and bio complete. Multi-agent audit and recruiters use this data.
          </p>
        </div>
      </div>

      {/* Profile Completeness Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs transition">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-indigo-600" />
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Profile Completeness</span>
              <span
                className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                  completenessPercent === 100
                    ? 'bg-emerald-100 text-emerald-800'
                    : completenessPercent >= 60
                    ? 'bg-indigo-100 text-indigo-800'
                    : 'bg-amber-100 text-amber-800'
                }`}
              >
                {completenessPercent}% Complete ({completedCount}/{completenessSections.length})
              </span>
            </div>
            <p className="text-xs text-slate-600">
              {completenessPercent === 100
                ? 'Your profile is comprehensive! Ready for AI audits, skill verification, and recruiter simulation.'
                : needsImprovementCount > 0 && emptyCount > 0
                ? `${needsImprovementCount} section${needsImprovementCount > 1 ? 's need' : ' needs'} improvement and ${emptyCount} section${emptyCount > 1 ? 's are' : ' is'} empty. Click any card below to improve.`
                : needsImprovementCount > 0
                ? `${needsImprovementCount} section${needsImprovementCount > 1 ? 's need' : ' needs'} improvement for full quality. Click any card below to improve.`
                : `${emptyCount} section${emptyCount > 1 ? 's' : ''} still empty or incomplete. Click any card below to complete it.`}
            </p>
          </div>

          {/* Progress Bar */}
          <div className="w-full md:w-64 space-y-1">
            <div className="h-2.5 w-full bg-slate-100 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-500 rounded-full ${
                  completenessPercent === 100
                    ? 'bg-emerald-500'
                    : completenessPercent >= 60
                    ? 'bg-indigo-600'
                    : 'bg-amber-500'
                }`}
                style={{ width: `${completenessPercent}%` }}
              />
            </div>
          </div>
        </div>

        {/* Section Chips */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-7 gap-2 pt-4 mt-4 border-t border-slate-100">
          {completenessSections.map((sec) => {
            const isComplete = sec.status === 'complete';
            const isNeedsImprovement = sec.status === 'needs-improvement';
            return (
              <button
                key={sec.id}
                type="button"
                onClick={() => setActiveTab(sec.tab)}
                className={`p-2.5 rounded-xl border text-left transition flex flex-col justify-between min-h-[76px] ${
                  isComplete
                    ? 'border-emerald-200 bg-emerald-50/50 hover:bg-emerald-100/50'
                    : isNeedsImprovement
                    ? 'border-amber-300 bg-amber-50/70 hover:bg-amber-100/70 shadow-xs'
                    : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100/50'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-1 mb-1">
                    <span className="text-xs font-semibold text-slate-800 truncate">{sec.title}</span>
                    {isComplete ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" aria-label="Complete" />
                    ) : (
                      <AlertCircle className="w-3.5 h-3.5 text-amber-600 shrink-0" aria-label={isNeedsImprovement ? 'Needs improvement' : 'Incomplete'} />
                    )}
                  </div>
                  {isNeedsImprovement && (
                    <span className="inline-block text-[10px] font-bold uppercase tracking-wider text-amber-700 bg-amber-100/80 px-1.5 py-0.5 rounded">
                      Needs improvement
                    </span>
                  )}
                </div>
                <div className="mt-1">
                  <p
                    className={`text-[11px] font-medium leading-tight ${
                      isComplete
                        ? 'text-emerald-700 truncate'
                        : isNeedsImprovement
                        ? 'text-amber-800 line-clamp-2'
                        : 'text-slate-500 truncate'
                    }`}
                    title={isNeedsImprovement ? sec.tip : sec.detail}
                  >
                    {isNeedsImprovement ? sec.tip : sec.detail}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 overflow-x-auto space-x-8">
        <button
          type="button"
          onClick={() => setActiveTab('basics')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition whitespace-nowrap ${
            activeTab === 'basics'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <UserCheck className="w-4 h-4" />
          General & Bio
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('skills')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition whitespace-nowrap ${
            activeTab === 'skills'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Code2 className="w-4 h-4" />
          Skills ({skillsPage?.totalElements || 0})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('projects')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition whitespace-nowrap ${
            activeTab === 'projects'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Briefcase className="w-4 h-4" />
          Projects ({projectsPage?.totalElements || 0})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('education')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition whitespace-nowrap ${
            activeTab === 'education'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <GraduationCap className="w-4 h-4" />
          Education ({educationPage?.totalElements || 0})
        </button>
      </div>

      {/* TAB 1: General & Bio */}
      {activeTab === 'basics' && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs p-6">
          {isProfileLoading ? (
            <CardSkeleton count={2} />
          ) : (
            <form onSubmit={handleSaveBasics} className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="md:col-span-2">
                  <label htmlFor="headline" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Professional Headline <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="headline"
                    type="text"
                    required
                    maxLength={120}
                    value={headline}
                    onChange={(e) => setHeadline(e.target.value)}
                    placeholder="e.g. Aspiring Full Stack Java Developer | Spring Boot, PostgreSQL, React"
                    className={`mt-1 block w-full px-3.5 py-2.5 rounded-xl border text-sm transition ${
                      headline.trim().length > 0 && headline.trim().length < 40
                        ? 'border-amber-300 focus:ring-amber-500 focus:border-amber-500'
                        : 'border-slate-300 focus:ring-indigo-500 focus:border-indigo-500'
                    }`}
                  />
                  {/* Real-time word/character guide */}
                  <div className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-xs">
                    <div className="flex items-center gap-1.5">
                      {headline.trim().length === 0 ? (
                        <span className="text-slate-500">
                          Guide: 40–120 characters recommended (include target role & 2-3 key technologies).
                        </span>
                      ) : headline.trim().length < 40 ? (
                        <span className="text-amber-600 font-medium flex items-center gap-1">
                          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                          Needs improvement: {40 - headline.trim().length} more characters needed to reach 40-character minimum.
                        </span>
                      ) : (
                        <span className="text-emerald-600 font-medium flex items-center gap-1">
                          <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                          Strong headline length (min 40 characters met).
                        </span>
                      )}
                    </div>
                    <div className="text-slate-500 font-mono flex items-center gap-1.5 ml-auto">
                      <span>{countWords(headline)} {countWords(headline) === 1 ? 'word' : 'words'}</span>
                      <span className="text-slate-300">·</span>
                      <span className={headline.trim().length > 0 && headline.trim().length < 40 ? 'text-amber-600 font-semibold' : ''}>
                        {headline.length}/120 chars
                      </span>
                    </div>
                  </div>
                </div>

                <div className="md:col-span-2">
                  <label htmlFor="about" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    About / Summary
                  </label>
                  <textarea
                    id="about"
                    rows={4}
                    maxLength={2000}
                    value={about}
                    onChange={(e) => setAbout(e.target.value)}
                    placeholder="Briefly describe your passions, core stack, and career aspirations..."
                    className={`mt-1 block w-full px-3.5 py-2.5 rounded-xl border text-sm transition ${
                      about.trim().length > 0 && about.trim().length < 100
                        ? 'border-amber-300 focus:ring-amber-500 focus:border-amber-500'
                        : 'border-slate-300 focus:ring-indigo-500 focus:border-indigo-500'
                    }`}
                  />
                  {/* Real-time word/character guide */}
                  <div className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-xs">
                    <div className="flex items-center gap-1.5">
                      {about.trim().length === 0 ? (
                        <span className="text-slate-500">
                          Guide: at least 100 characters recommended (2-3 sentences on your technical focus & passion).
                        </span>
                      ) : about.trim().length < 100 ? (
                        <span className="text-amber-600 font-medium flex items-center gap-1">
                          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                          Needs improvement: {100 - about.trim().length} more characters needed to reach 100-character minimum.
                        </span>
                      ) : (
                        <span className="text-emerald-600 font-medium flex items-center gap-1">
                          <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                          Strong summary length (min 100 characters met).
                        </span>
                      )}
                    </div>
                    <div className="text-slate-500 font-mono flex items-center gap-1.5 ml-auto">
                      <span>{countWords(about)} {countWords(about) === 1 ? 'word' : 'words'}</span>
                      <span className="text-slate-300">·</span>
                      <span className={about.trim().length > 0 && about.trim().length < 100 ? 'text-amber-600 font-semibold' : ''}>
                        {about.length}/2000 chars
                      </span>
                    </div>
                  </div>
                </div>

                <div>
                  <label htmlFor="githubUsername" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                    <Github className="w-3.5 h-3.5" />
                    GitHub Username
                  </label>
                  <div className="mt-1 relative rounded-xl shadow-xs">
                    <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400 text-sm">
                      github.com/
                    </span>
                    <input
                      id="githubUsername"
                      type="text"
                      maxLength={39}
                      value={githubUsername}
                      onChange={handleGithubUsernameChange}
                      placeholder="octocat"
                      className={`block w-full pl-28 pr-3.5 py-2.5 rounded-xl border ${
                        githubUsername && !isValidGithubUsername(githubUsername)
                          ? 'border-rose-300 focus:ring-rose-500 focus:border-rose-500'
                          : 'border-slate-300 focus:ring-indigo-500 focus:border-indigo-500'
                      } text-sm`}
                    />
                  </div>
                  {githubUsername && !isValidGithubUsername(githubUsername) ? (
                    <p className="mt-1 text-xs text-rose-600 font-medium flex items-center gap-1" role="alert">
                      <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      Enter only your GitHub username, for example octocat
                    </p>
                  ) : (
                    <p className="mt-1 text-xs text-slate-400">Used by ProofOfSkillAgent to verify skills against public repos.</p>
                  )}
                </div>

                <div>
                  <label htmlFor="targetRole" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Target Role
                  </label>
                  <input
                    id="targetRole"
                    type="text"
                    maxLength={100}
                    value={targetRole}
                    onChange={(e) => setTargetRole(e.target.value)}
                    placeholder="e.g. Java Backend Developer"
                    className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label htmlFor="phone" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Phone Number
                  </label>
                  <input
                    id="phone"
                    type="text"
                    maxLength={50}
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+1 (555) 000-0000"
                    className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label htmlFor="location" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Location
                  </label>
                  <input
                    id="location"
                    type="text"
                    maxLength={100}
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                    placeholder="San Francisco, CA or Remote"
                    className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label htmlFor="linkedinUrl" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    LinkedIn Profile URL
                  </label>
                  <input
                    id="linkedinUrl"
                    type="url"
                    maxLength={255}
                    value={linkedinUrl}
                    onChange={(e) => setLinkedinUrl(e.target.value)}
                    placeholder="https://linkedin.com/in/username"
                    className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label htmlFor="portfolioUrl" className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Personal Portfolio / Website
                  </label>
                  <input
                    id="portfolioUrl"
                    type="url"
                    maxLength={255}
                    value={portfolioUrl}
                    onChange={(e) => setPortfolioUrl(e.target.value)}
                    placeholder="https://myportfolio.dev"
                    className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>

              <div className="pt-4 border-t border-slate-100 flex justify-end">
                <button
                  type="submit"
                  disabled={updateProfileMutation.isPending}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-md shadow-indigo-100 disabled:opacity-60 transition"
                >
                  {updateProfileMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                  Save Changes
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* TAB 2: Skills */}
      {activeTab === 'skills' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-bold text-slate-900">Claimed Technical Skills</h2>
            <button
              type="button"
              onClick={() => {
                setEditingSkill(null);
                setSkillName('');
                setSkillLevel('INTERMEDIATE');
                setIsSkillModalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-xs transition"
            >
              <Plus className="w-4 h-4" />
              Add Skill
            </button>
          </div>

          {isSkillsLoading ? (
            <CardSkeleton count={3} />
          ) : !skillsPage?.content || skillsPage.content.length === 0 ? (
            <EmptyState
              title="No skills added yet"
              description="Add the languages, frameworks, and databases you work with."
              actionText="Add First Skill"
              onAction={() => {
                setSkillName('');
                setIsSkillModalOpen(true);
              }}
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {skillsPage.content.map((sk) => (
                <div
                  key={sk.id}
                  className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs flex items-center justify-between"
                >
                  <div>
                    <h3 className="font-semibold text-slate-900">{sk.name}</h3>
                    <div className="flex items-center gap-2 mt-1.5">
                      <span className="text-xs font-medium px-2 py-0.5 rounded-md bg-slate-100 text-slate-700">
                        {sk.claimedLevel}
                      </span>
                      <SkillBadge
                        status={sk.verificationStatus}
                        evidenceUrl={sk.evidenceUrl}
                        evidenceSummary={sk.evidenceSummary}
                      />
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingSkill(sk);
                        setSkillName(sk.name);
                        setSkillLevel(sk.claimedLevel);
                        setIsSkillModalOpen(true);
                      }}
                      className="p-1.5 text-slate-400 hover:text-indigo-600 hover:bg-slate-50 rounded-lg transition"
                      aria-label={`Edit ${sk.name}`}
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => deleteSkillMutation.mutate(sk.id)}
                      className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition"
                      aria-label={`Delete ${sk.name}`}
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: Projects */}
      {activeTab === 'projects' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-bold text-slate-900">Coursework & Personal Projects</h2>
            <button
              type="button"
              onClick={() => openProjectModal()}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-xs transition"
            >
              <Plus className="w-4 h-4" />
              Add Project
            </button>
          </div>

          {isProjectsLoading ? (
            <CardSkeleton count={2} />
          ) : !projectsPage?.content || projectsPage.content.length === 0 ? (
            <EmptyState
              title="No projects listed"
              description="Add academic or personal projects. ExperienceTranslatorAgent can rewrite your bullet points into STAR format."
              actionText="Add First Project"
              onAction={() => openProjectModal()}
            />
          ) : (
            <div className="space-y-4">
              {projectsPage.content.map((proj) => (
                <div key={proj.id} className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs space-y-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-lg font-bold text-slate-900">{proj.title}</h3>
                      {proj.role && <p className="text-xs font-semibold text-indigo-600 mt-0.5">{proj.role}</p>}
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => openProjectModal(proj)}
                        className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-slate-50 rounded-xl transition"
                        aria-label={`Edit ${proj.title}`}
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => deleteProjectMutation.mutate(proj.id)}
                        className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition"
                        aria-label={`Delete ${proj.title}`}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {proj.description && <p className="text-sm text-slate-600">{proj.description}</p>}

                  {proj.technologies && (
                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {proj.technologies.split(',').map((tech, i) => (
                        <span key={i} className="px-2.5 py-0.5 bg-slate-100 text-slate-700 rounded-md text-xs font-medium">
                          {tech.trim()}
                        </span>
                      ))}
                    </div>
                  )}

                  {proj.bullets && proj.bullets.length > 0 && (
                    <ul className="list-disc list-inside space-y-1 text-sm text-slate-700 pt-2 border-t border-slate-100">
                      {proj.bullets.map((bullet, idx) => (
                        <li key={idx} className="leading-relaxed">{bullet}</li>
                      ))}
                    </ul>
                  )}

                  <div className="flex items-center gap-4 text-xs font-medium pt-2 text-slate-500">
                    {proj.repoUrl && (
                      <a href={proj.repoUrl} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 hover:text-indigo-600">
                        <Github className="w-3.5 h-3.5" />
                        Repository
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                    {proj.liveUrl && (
                      <a href={proj.liveUrl} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 hover:text-indigo-600">
                        Live Demo
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 4: Education */}
      {activeTab === 'education' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-bold text-slate-900">Education & Degrees</h2>
            <button
              type="button"
              onClick={() => openEduModal()}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-xs transition"
            >
              <Plus className="w-4 h-4" />
              Add Education
            </button>
          </div>

          {isEducationLoading ? (
            <CardSkeleton count={2} />
          ) : !educationPage?.content || educationPage.content.length === 0 ? (
            <EmptyState
              title="No education records found"
              description="Add your degree, university, GPA, and academic coursework."
              actionText="Add Education"
              onAction={() => openEduModal()}
            />
          ) : (
            <div className="space-y-4">
              {educationPage.content.map((edu) => (
                <div key={edu.id} className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs flex items-start justify-between">
                  <div className="space-y-1">
                    <h3 className="text-base font-bold text-slate-900">{edu.degree}</h3>
                    <p className="text-sm font-medium text-indigo-600">{edu.institution}</p>
                    {edu.fieldOfStudy && <p className="text-xs text-slate-500">Field: {edu.fieldOfStudy}</p>}
                    {edu.gradeOrGpa && <p className="text-xs text-slate-500">GPA / Grade: {edu.gradeOrGpa}</p>}
                    {edu.activities && <p className="text-xs text-slate-600 mt-2">{edu.activities}</p>}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => openEduModal(edu)}
                      className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-slate-50 rounded-xl transition"
                      aria-label={`Edit ${edu.degree}`}
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => deleteEduMutation.mutate(edu.id)}
                      className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition"
                      aria-label={`Delete ${edu.degree}`}
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* SKILL MODAL */}
      <Modal
        isOpen={isSkillModalOpen}
        onClose={() => setIsSkillModalOpen(false)}
        title={editingSkill ? 'Edit Skill Proficiency' : 'Add Technical Skill'}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (editingSkill) {
              updateSkillMutation.mutate({ id: editingSkill.id, claimedLevel: skillLevel });
            } else {
              addSkillMutation.mutate({ name: skillName.trim(), claimedLevel: skillLevel });
            }
          }}
          className="space-y-4"
        >
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Skill Name
            </label>
            <input
              type="text"
              required
              disabled={!!editingSkill}
              value={skillName}
              onChange={(e) => setSkillName(e.target.value)}
              placeholder="e.g. Java, Spring Boot, React, Docker"
              className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500 disabled:bg-slate-50"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Claimed Proficiency Level
            </label>
            <select
              value={skillLevel}
              onChange={(e) => setSkillLevel(e.target.value as SkillProficiency)}
              className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-indigo-500 focus:border-indigo-500"
            >
              <option value="BEGINNER">BEGINNER - Basic academic or tutorial familiarity</option>
              <option value="INTERMEDIATE">INTERMEDIATE - Built projects with this skill</option>
              <option value="ADVANCED">ADVANCED - Production or comprehensive project depth</option>
            </select>
          </div>

          <div className="pt-4 border-t border-slate-100 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setIsSkillModalOpen(false)}
              className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={addSkillMutation.isPending || updateSkillMutation.isPending}
              className="px-5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60"
            >
              {editingSkill ? 'Save' : 'Add Skill'}
            </button>
          </div>
        </form>
      </Modal>

      {/* PROJECT MODAL */}
      <Modal
        isOpen={isProjectModalOpen}
        onClose={() => setIsProjectModalOpen(false)}
        title={editingProject ? 'Edit Project' : 'Add Project'}
        maxWidth="xl"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            saveProjectMutation.mutate();
          }}
          className="space-y-4"
        >
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Project Title <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              maxLength={150}
              value={projectTitle}
              onChange={(e) => setProjectTitle(e.target.value)}
              placeholder="e.g. Distributed Task Scheduler"
              className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Role in Project
              </label>
              <input
                type="text"
                value={projectRole}
                onChange={(e) => setProjectRole(e.target.value)}
                placeholder="e.g. Backend Lead"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Technologies Used
              </label>
              <input
                type="text"
                value={projectTech}
                onChange={(e) => setProjectTech(e.target.value)}
                placeholder="Java, Spring Boot, Redis, Kafka"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Brief Description
            </label>
            <textarea
              rows={2}
              value={projectDescription}
              onChange={(e) => setProjectDescription(e.target.value)}
              placeholder="What problem does this project solve?"
              className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
            />
          </div>

          <div>
            <div className="flex justify-between items-center mb-1">
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Achievement Bullets (Max 8)
              </label>
              {projectBullets.length < 8 && (
                <button
                  type="button"
                  onClick={() => setProjectBullets([...projectBullets, ''])}
                  className="text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                >
                  + Add Bullet
                </button>
              )}
            </div>
            <div className="space-y-2">
              {projectBullets.map((bullet, idx) => (
                <div key={idx} className="flex gap-2">
                  <input
                    type="text"
                    maxLength={300}
                    value={bullet}
                    onChange={(e) => {
                      const updated = [...projectBullets];
                      updated[idx] = e.target.value;
                      setProjectBullets(updated);
                    }}
                    placeholder="Action verb + what + tech + measurable impact"
                    className="block w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                  />
                  {projectBullets.length > 1 && (
                    <button
                      type="button"
                      onClick={() => setProjectBullets(projectBullets.filter((_, i) => i !== idx))}
                      className="p-2 text-slate-400 hover:text-red-500"
                      aria-label="Remove bullet"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Repository URL
              </label>
              <input
                type="url"
                value={projectRepo}
                onChange={(e) => setProjectRepo(e.target.value)}
                placeholder="https://github.com/user/repo"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Live URL
              </label>
              <input
                type="url"
                value={projectLive}
                onChange={(e) => setProjectLive(e.target.value)}
                placeholder="https://demo.app"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
          </div>

          <div className="pt-4 border-t border-slate-100 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setIsProjectModalOpen(false)}
              className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saveProjectMutation.isPending}
              className="px-5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60"
            >
              {editingProject ? 'Save Changes' : 'Create Project'}
            </button>
          </div>
        </form>
      </Modal>

      {/* EDUCATION MODAL */}
      <Modal
        isOpen={isEducationModalOpen}
        onClose={() => setIsEducationModalOpen(false)}
        title={editingEdu ? 'Edit Education' : 'Add Education Record'}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            saveEduMutation.mutate();
          }}
          className="space-y-4"
        >
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Degree <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              value={eduDegree}
              onChange={(e) => setEduDegree(e.target.value)}
              placeholder="e.g. Bachelor of Technology in Computer Science"
              className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Institution / College <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              value={eduInstitution}
              onChange={(e) => setEduInstitution(e.target.value)}
              placeholder="e.g. Stanford University or Delhi Technological University"
              className="mt-1 block w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Field of Study
              </label>
              <input
                type="text"
                value={eduField}
                onChange={(e) => setEduField(e.target.value)}
                placeholder="Computer Science & Engineering"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Grade / GPA
              </label>
              <input
                type="text"
                value={eduGrade}
                onChange={(e) => setEduGrade(e.target.value)}
                placeholder="3.8 / 4.0 or 8.9 CGPA"
                className="mt-1 block w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm"
              />
            </div>
          </div>

          <div className="pt-4 border-t border-slate-100 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setIsEducationModalOpen(false)}
              className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saveEduMutation.isPending}
              className="px-5 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60"
            >
              {editingEdu ? 'Save Changes' : 'Add Record'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
