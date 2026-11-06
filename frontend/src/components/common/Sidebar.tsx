import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  UserCheck,
  FileText,
  Users,
  Inbox,
  ShieldCheck,
  Target,
  CalendarCheck2,
  UploadCloud,
  Briefcase,
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

const NAV_ITEMS = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Profile Builder', path: '/profile', icon: UserCheck },
  { name: 'Audit Report', path: '/audit', icon: FileText },
  { name: 'Job Match', path: '/job-match', icon: Briefcase },
  { name: 'Recruiter Sim', path: '/recruiter-simulation', icon: Users },
  { name: 'Suggestions Inbox', path: '/suggestions', icon: Inbox },
  { name: 'Skill Verification', path: '/skills-verify', icon: ShieldCheck },
  { name: 'Skill Gap', path: '/skill-gap', icon: Target },
  { name: 'Weekly Coach', path: '/coach', icon: CalendarCheck2 },
  { name: 'Resume Upload', path: '/resume', icon: UploadCloud },
];

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  return (
    <>
      {/* Mobile backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 top-16 z-40 bg-slate-900/40 lg:hidden backdrop-blur-xs"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar container */}
      <aside
        className={`fixed top-16 bottom-0 left-0 z-40 lg:z-30 w-64 bg-white border-r border-slate-200 transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        } flex flex-col justify-between overflow-y-auto`}
        aria-label="Sidebar navigation"
      >
        <div className="p-4 space-y-1">
          <p className="px-3 text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">
            Features & Agents
          </p>
          <nav className="space-y-1" aria-label="Main Navigation">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={() => {
                    if (window.innerWidth < 1024) {
                      onClose();
                    }
                  }}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition ${
                      isActive
                        ? 'bg-indigo-50 text-indigo-700 font-semibold shadow-xs'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                    }`
                  }
                >
                  <Icon className="w-4 h-4 flex-shrink-0" />
                  <span>{item.name}</span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        <div className="p-4 border-t border-slate-100 bg-slate-50/50">
          <div className="p-3 bg-white rounded-xl border border-slate-200 text-xs">
            <p className="font-semibold text-slate-800">CareerPilot Freshers</p>
            <p className="text-slate-500 mt-0.5">Multi-Agent AI Profile Coach</p>
          </div>
        </div>
      </aside>
    </>
  );
};
