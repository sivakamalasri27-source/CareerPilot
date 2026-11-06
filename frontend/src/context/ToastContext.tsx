import React, { createContext, useContext, useState, useCallback } from 'react';
import { AlertCircle, CheckCircle, Info, AlertTriangle, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  title?: string;
  message: string;
  retryAfterSeconds?: number;
  remainingSeconds?: number;
}

interface ToastContextType {
  showToast: (toast: Omit<ToastItem, 'id'>) => void;
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    ({ type, title, message, retryAfterSeconds }: Omit<ToastItem, 'id'>) => {
      const id = Math.random().toString(36).substring(2, 9);
      const newToast: ToastItem = {
        id,
        type,
        title,
        message,
        retryAfterSeconds,
        remainingSeconds: retryAfterSeconds,
      };

      setToasts((prev) => [...prev, newToast]);

      // If retry-after countdown exists, tick every second
      if (retryAfterSeconds && retryAfterSeconds > 0) {
        const intervalId = setInterval(() => {
          setToasts((prev) =>
            prev.map((t) => {
              if (t.id === id && t.remainingSeconds !== undefined) {
                if (t.remainingSeconds <= 1) {
                  clearInterval(intervalId);
                  setTimeout(() => removeToast(id), 1000);
                  return { ...t, remainingSeconds: 0 };
                }
                return { ...t, remainingSeconds: t.remainingSeconds - 1 };
              }
              return t;
            })
          );
        }, 1000);
      } else {
        // Auto-dismiss standard toasts after 5 seconds
        setTimeout(() => {
          removeToast(id);
        }, 5000);
      }
    },
    [removeToast]
  );

  return (
    <ToastContext.Provider value={{ showToast, removeToast }}>
      {children}
      <div
        className="fixed bottom-4 right-4 z-50 flex flex-col space-y-2 max-w-md w-full px-4 sm:px-0"
        role="region"
        aria-label="Notifications"
      >
        {toasts.map((toast) => {
          const isError = toast.type === 'error';
          const isSuccess = toast.type === 'success';
          const isWarning = toast.type === 'warning';

          return (
            <div
              key={toast.id}
              role="alert"
              className={`p-4 rounded-xl shadow-lg border flex items-start space-x-3 transition-all duration-300 animate-slide-in ${
                isError
                  ? 'bg-red-50 border-red-200 text-red-900'
                  : isSuccess
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                  : isWarning
                  ? 'bg-amber-50 border-amber-200 text-amber-900'
                  : 'bg-indigo-50 border-indigo-200 text-indigo-900'
              }`}
            >
              <div className="flex-shrink-0 mt-0.5">
                {isError && <AlertCircle className="w-5 h-5 text-red-600" />}
                {isSuccess && <CheckCircle className="w-5 h-5 text-emerald-600" />}
                {isWarning && <AlertTriangle className="w-5 h-5 text-amber-600" />}
                {!isError && !isSuccess && !isWarning && <Info className="w-5 h-5 text-indigo-600" />}
              </div>
              <div className="flex-1 text-sm">
                {toast.title && <p className="font-semibold">{toast.title}</p>}
                <p className={toast.title ? 'mt-0.5 text-xs opacity-90' : ''}>{toast.message}</p>
                {toast.remainingSeconds !== undefined && toast.remainingSeconds > 0 && (
                  <p className="mt-1 text-xs font-medium text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full inline-block">
                    Retry available in: {toast.remainingSeconds}s
                  </p>
                )}
              </div>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-slate-400 hover:text-slate-700 transition p-1"
                aria-label="Close notification"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
