import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { User, LoginRequest, RegisterRequest } from '../types/auth';
import { authApi } from '../api/authApi';
import { setAccessToken, setAuthFailureHandler } from '../api/client';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore network errors on logout
    } finally {
      setAccessToken(null);
      setUser(null);
      localStorage.removeItem('careerpilot_refresh_token');
    }
  }, []);

  // Hook up automatic logout on refresh token failure in Axios interceptor
  useEffect(() => {
    setAuthFailureHandler(() => {
      setAccessToken(null);
      setUser(null);
      localStorage.removeItem('careerpilot_refresh_token');
    });
  }, []);

  // Check stored refresh token on initial app load to re-hydrate memory access token
  useEffect(() => {
    const initAuth = async () => {
      const storedRefreshToken = localStorage.getItem('careerpilot_refresh_token');
      if (!storedRefreshToken) {
        setIsLoading(false);
        return;
      }

      try {
        const authData = await authApi.refresh(storedRefreshToken);
        setUser(authData.user);
      } catch {
        // Stored token is invalid or expired
        localStorage.removeItem('careerpilot_refresh_token');
        setAccessToken(null);
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, []);

  const login = async (credentials: LoginRequest) => {
    const authData = await authApi.login(credentials);
    setUser(authData.user);
  };

  const register = async (data: RegisterRequest) => {
    const authData = await authApi.register(data);
    setUser(authData.user);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
