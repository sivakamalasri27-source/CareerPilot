import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Navbar } from './Navbar';
import { Sidebar } from './Sidebar';
import { RouteAwareErrorBoundary } from './ErrorBoundary';

export const Layout: React.FC = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col text-slate-800">
      {/* Full width header across top of app */}
      <Navbar
        isSidebarOpen={isSidebarOpen}
        onToggleSidebar={() => setIsSidebarOpen((prev) => !prev)}
      />

      {/* Main app body: sidebar + main content */}
      <div className="flex-1 flex w-full relative">
        <Sidebar isOpen={isSidebarOpen} onClose={() => setIsSidebarOpen(false)} />
        <main className="flex-1 min-w-0 lg:ml-64 p-4 sm:p-6 lg:p-8 w-full overflow-y-auto transition-all">
          <div className="max-w-7xl mx-auto w-full">
            <RouteAwareErrorBoundary>
              <Outlet />
            </RouteAwareErrorBoundary>
          </div>
        </main>
      </div>
    </div>
  );
};
