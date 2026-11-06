import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { RegisterPage } from '../pages/RegisterPage';
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { authApi } from '../api/authApi';

vi.mock('../api/authApi', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    refresh: vi.fn(),
    getCurrentUser: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const renderRegisterPage = () => {
  return render(
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  );
};

describe('RegisterPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders registration form with Full Name, Email, and Password fields', () => {
    renderRegisterPage();

    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('displays inline error when Full Name is left blank', async () => {
    renderRegisterPage();

    const submitBtn = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByTestId('fullname-error')).toHaveTextContent('Full name is required');
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('submits registration with trimmed fullName using the exact field name expected by backend', async () => {
    (authApi.register as any).mockResolvedValue({
      accessToken: 'fake-access-token',
      refreshToken: 'fake-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: 1,
        name: 'John Fresher',
        email: 'john.fresher@college.edu',
        role: 'USER',
      },
    });

    renderRegisterPage();

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByLabelText(/password/i);

    // Type with leading/trailing spaces
    fireEvent.change(nameInput, { target: { value: '  John Fresher  ' } });
    fireEvent.change(emailInput, { target: { value: '  john.fresher@college.edu  ' } });
    fireEvent.change(passwordInput, { target: { value: 'SecurePass123!' } });

    const submitBtn = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledTimes(1);
      expect(authApi.register).toHaveBeenCalledWith({
        fullName: 'John Fresher',
        email: 'john.fresher@college.edu',
        password: 'SecurePass123!',
      });
    });

    expect(screen.queryByTestId('fullname-error')).not.toBeInTheDocument();
  });

  it('displays inline error under Full Name field when backend returns validation error', async () => {
    (authApi.register as any).mockRejectedValue({
      message: 'Validation failed',
      validationErrors: {
        fullName: 'Full name must be between 2 and 100 characters',
      },
    });

    renderRegisterPage();

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByLabelText(/password/i);

    fireEvent.change(nameInput, { target: { value: 'A' } });
    fireEvent.change(emailInput, { target: { value: 'test@college.edu' } });
    fireEvent.change(passwordInput, { target: { value: 'Password123!' } });

    const submitBtn = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(submitBtn);

    const errorEl = await screen.findByTestId('fullname-error');
    expect(errorEl).toHaveTextContent('Full name must be between 2 and 100 characters');
  });
});
