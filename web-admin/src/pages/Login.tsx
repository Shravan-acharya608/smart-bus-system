import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { Bus, Mail, Lock, AlertCircle } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { apiClient } from '../lib/axios';
import { API_ENDPOINTS } from '../config/api';
import { ApiResponse, UserRole } from '../types';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

interface LoginResponseData {
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: UserRole;
    organizationId?: string;
  };
  tokens?: {
    accessToken: string;
    refreshToken: string;
  };
  accessToken?: string;
  refreshToken?: string;
}

const ALLOWED_ROLES: UserRole[] = ['ADMIN', 'BUS_OWNER'];

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormValues) => {
    setServerError(null);
    try {
      const response = await apiClient.post<ApiResponse<LoginResponseData>>(
        API_ENDPOINTS.AUTH.LOGIN,
        data
      );

      if (!response.data.success || !response.data.data) {
        throw new Error(response.data.error?.message || 'Login failed');
      }

      const resData = response.data.data;
      const user = resData.user;
      const accessToken = resData.tokens?.accessToken || (resData as unknown as { accessToken?: string }).accessToken;
      const refreshToken = resData.tokens?.refreshToken || (resData as unknown as { refreshToken?: string }).refreshToken;

      if (!accessToken) {
        throw new Error('Authentication token missing from server response');
      }

      if (!ALLOWED_ROLES.includes(user.role)) {
        setServerError(
          `Access denied. Only Admin and Bus Owner accounts can access this portal. Your role is: ${user.role}`
        );
        return;
      }

      setAuth(user, accessToken, refreshToken || '');
      navigate('/');
    } catch (err: unknown) {
      if (err instanceof Error) {
        setServerError(err.message);
      } else {
        setServerError('An unexpected error occurred. Please try again.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 to-blue-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-2xl shadow-xl p-8">
          {/* Logo */}
          <div className="flex flex-col items-center mb-8">
            <div className="w-14 h-14 bg-primary-600 rounded-2xl flex items-center justify-center mb-3">
              <Bus size={28} className="text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">SmartBus Admin</h1>
            <p className="text-sm text-gray-500 mt-1">Sign in to your admin portal</p>
          </div>

          {/* Error alert */}
          {serverError && (
            <div className="mb-5 flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-lg">
              <AlertCircle size={18} className="text-red-500 flex-shrink-0 mt-0.5" />
              <p className="text-sm text-red-700">{serverError}</p>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
            <Input
              label="Email address"
              type="email"
              placeholder="admin@example.com"
              icon={<Mail size={16} />}
              error={errors.email?.message}
              autoComplete="email"
              {...register('email')}
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              icon={<Lock size={16} />}
              error={errors.password?.message}
              autoComplete="current-password"
              {...register('password')}
            />
            <Button
              type="submit"
              variant="primary"
              size="lg"
              loading={isSubmitting}
              className="w-full mt-2"
            >
              Sign In
            </Button>
          </form>

          <p className="mt-6 text-center text-xs text-gray-400">
            Access restricted to Admin and Bus Owner accounts
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
