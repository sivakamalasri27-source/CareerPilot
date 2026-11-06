import React from 'react';
import { VerificationStatus } from '../../types/profile';
import { CheckCircle2, AlertTriangle, HelpCircle, ExternalLink } from 'lucide-react';

interface SkillBadgeProps {
  status: VerificationStatus;
  skillName?: string;
  evidenceUrl?: string | null;
  evidenceSummary?: string | null;
  className?: string;
}

export const SkillBadge: React.FC<SkillBadgeProps> = ({
  status,
  skillName,
  evidenceUrl,
  evidenceSummary,
  className = '',
}) => {
  const getBadgeStyle = () => {
    switch (status) {
      case 'VERIFIED':
        return {
          bg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
          dot: 'bg-emerald-500',
          icon: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" aria-hidden="true" />,
          label: 'VERIFIED',
          ariaLabel: `${skillName || 'Skill'} is verified with repository evidence`,
        };
      case 'PARTIAL':
        return {
          bg: 'bg-amber-50 text-amber-700 border-amber-200',
          dot: 'bg-amber-500',
          icon: <AlertTriangle className="w-3.5 h-3.5 text-amber-600" aria-hidden="true" />,
          label: 'PARTIAL',
          ariaLabel: `${skillName || 'Skill'} is partially verified with weak or single repository evidence`,
        };
      case 'UNVERIFIED':
      default:
        return {
          bg: 'bg-slate-100 text-slate-600 border-slate-200',
          dot: 'bg-slate-400',
          icon: <HelpCircle className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />,
          label: 'UNVERIFIED',
          ariaLabel: `${skillName || 'Skill'} is unverified with no public GitHub evidence`,
        };
    }
  };

  const badge = getBadgeStyle();

  return (
    <div
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${badge.bg} ${className}`}
      aria-label={badge.ariaLabel}
      title={evidenceSummary || badge.label}
      data-testid="skill-badge"
    >
      {badge.icon}
      <span>{badge.label}</span>
      {evidenceUrl && (
        <a
          href={evidenceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="ml-1 text-slate-400 hover:text-slate-700 transition"
          aria-label={`View evidence repository for ${skillName || 'skill'}`}
          onClick={(e) => e.stopPropagation()}
        >
          <ExternalLink className="w-3 h-3" />
        </a>
      )}
    </div>
  );
};
