import React from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  ShieldCheck,
  Target,
  Users,
  CalendarCheck2,
  ArrowRight,
  UploadCloud,
  CheckCircle2,
} from 'lucide-react';
import { Navbar } from '../components/common/Navbar';

export const LandingPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      {/* Hero Section */}
      <section className="relative overflow-hidden pt-16 pb-20 lg:pt-24 lg:pb-32">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-indigo-50 border border-indigo-200 text-indigo-700 text-xs font-semibold mb-6">
            <Sparkles className="w-4 h-4 text-indigo-600" />
            AI-Powered Career Launchpad for Freshers
          </div>

          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-slate-900 tracking-tight max-w-4xl mx-auto leading-tight">
            Turn Your Fresher Profile Into An{' '}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-violet-600">
              Industry-Ready Career Engine
            </span>
          </h1>

          <p className="mt-6 text-lg sm:text-xl text-slate-600 max-w-2xl mx-auto font-normal">
            Automated multi-agent profile audits, recruiter panels, GitHub proof-of-skill verification,
            and weekly coaching to land your dream tech role.
          </p>

          <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              to="/register"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3.5 rounded-xl font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-lg shadow-indigo-200 transition transform hover:-translate-y-0.5"
            >
              Start Free Today
              <ArrowRight className="w-4 h-4" />
            </Link>
            <Link
              to="/login"
              className="w-full sm:w-auto inline-flex items-center justify-center px-7 py-3.5 rounded-xl font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 shadow-xs transition"
            >
              Sign In
            </Link>
          </div>

          {/* Social Proof Badges */}
          <div className="mt-12 flex flex-wrap items-center justify-center gap-6 text-sm text-slate-500">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              <span>Zero Fabricated Data</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              <span>Deterministic Skill Matching</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              <span>In-Memory Privacy First</span>
            </div>
          </div>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-20 bg-white border-t border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto mb-16">
            <h2 className="text-xs font-bold text-indigo-600 uppercase tracking-widest">
              Autonomous Intelligence
            </h2>
            <p className="mt-2 text-3xl font-extrabold text-slate-900 tracking-tight">
              A Complete Multi-Agent Coaching Stack
            </p>
            <p className="mt-4 text-slate-600">
              Six specialized AI agents work together to analyze your profile, simulate real hiring managers, and guide your daily progress.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-indigo-100 text-indigo-600 flex items-center justify-center mb-5">
                <Sparkles className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">Multi-Agent Profile Auditor</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Scores headline, about, skills, projects, and education from 0 to 100 with radar charts and highlights specific weak sentences with clear explanations.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-purple-100 text-purple-600 flex items-center justify-center mb-5">
                <Users className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">Recruiter Simulation Panel</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Simulate 10-second impressions, strengths, red flags, and YES/MAYBE/NO decisions from MNC HR, Startup Founder, and Tech Lead personas.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-600 flex items-center justify-center mb-5">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">GitHub Proof-of-Skill Agent</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Connect your GitHub to verify claimed skills against public repos. Receive VERIFIED, PARTIAL, or UNVERIFIED badges with linkable repository evidence.
              </p>
            </div>

            {/* Feature 4 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center mb-5">
                <Target className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">Deterministic Skill Gap Roadmap</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Compare your verified skills against curated industry roles like Java Backend, Full Stack, or AI Engineer to reveal your exact percentage match and roadmap.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-amber-100 text-amber-600 flex items-center justify-center mb-5">
                <CalendarCheck2 className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">Weekly Consistency Coach</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Receive exactly 3 bite-sized actionable tasks per week. Complete at least 2 tasks weekly to build and protect your consistency streak.
              </p>
            </div>

            {/* Feature 6 */}
            <div className="p-6 rounded-2xl bg-slate-50/70 border border-slate-200/80 hover:border-indigo-200 hover:shadow-md transition">
              <div className="w-12 h-12 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center mb-5">
                <UploadCloud className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">In-Memory Resume Parser</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                Upload your PDF resume without file persistence. AI extracts skills, projects, and bullets directly into your Suggestions Inbox for approval.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto py-8 bg-slate-900 text-slate-400 text-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p>&copy; {new Date().getFullYear()} CareerPilot. Built for aspiring fresher developers.</p>
          <div className="flex gap-6">
            <Link to="/login" className="hover:text-white transition">Sign In</Link>
            <Link to="/register" className="hover:text-white transition">Sign Up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
};
