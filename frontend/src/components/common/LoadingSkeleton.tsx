import React from 'react';

export const CardSkeleton: React.FC<{ count?: number }> = ({ count = 1 }) => {
  return (
    <>
      {Array.from({ length: count }).map((_, idx) => (
        <div key={idx} className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm animate-pulse space-y-4">
          <div className="h-5 bg-slate-200 rounded w-1/3"></div>
          <div className="space-y-2">
            <div className="h-4 bg-slate-100 rounded w-full"></div>
            <div className="h-4 bg-slate-100 rounded w-5/6"></div>
            <div className="h-4 bg-slate-100 rounded w-4/6"></div>
          </div>
          <div className="flex gap-2 pt-2">
            <div className="h-8 bg-slate-200 rounded w-20"></div>
            <div className="h-8 bg-slate-200 rounded w-24"></div>
          </div>
        </div>
      ))}
    </>
  );
};

export const TableSkeleton: React.FC<{ rows?: number }> = ({ rows = 4 }) => {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden animate-pulse">
      <div className="h-12 bg-slate-100 border-b border-slate-200"></div>
      <div className="divide-y divide-slate-100">
        {Array.from({ length: rows }).map((_, idx) => (
          <div key={idx} className="p-4 flex items-center justify-between">
            <div className="space-y-2 w-1/2">
              <div className="h-4 bg-slate-200 rounded w-3/4"></div>
              <div className="h-3 bg-slate-100 rounded w-1/2"></div>
            </div>
            <div className="h-6 bg-slate-200 rounded w-20"></div>
          </div>
        ))}
      </div>
    </div>
  );
};
