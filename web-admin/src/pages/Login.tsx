import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { Bus, Mail, Lock, AlertCircle, Settings, CheckCircle2, Globe, Server } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { apiClient } from '../lib/axios';
import { API_ENDPOINTS, getBackendBaseUrl } from '../config/api';
import { ApiResponse, UserRole } from '../types';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Modal from '../components/ui/Modal';
import axios from 'axios';

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
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [serverUrlInput, setServerUrlInput] = useState(getBackendBaseUrl());
  const [testState, setTestState] = useState<'idle' | 'testing' | 'success' | 'error'>('idle');
  const [testMessage, setTestMessage] = useState('');

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: 'admin@smartbus.local',
      password: 'Admin@123456',
    },
  });

  const handleTestAndSave = async (urlToSave: string) => {
    setTestState('testing');
    setTestMessage('Pinging backend server...');
    const cleaned = urlToSave.trim().replace(/\/+$/, '');
    const healthUrl = cleaned.endsWith('/api/v1') ? `${cleaned}/health` : `${cleaned}/api/v1/health`;

    try {
      const resp = await axios.get(healthUrl, { timeout: 10000 });
      if (resp.status >= 200 && resp.status < 300) {
        localStorage.setItem('smartbus_api_base_url', cleaned);
        setTestState('success');
        setTestMessage('Successfully connected to backend!');
        setTimeout(() => {
          setShowConfigModal(false);
          setTestState('idle');
          setServerError(null);
        }, 1200);
      } else {
        throw new Error(`HTTP ${resp.status}`);
      }
    } catch (err: unknown) {
      setTestState('error');
      const msg = err instanceof Error ? err.message : 'Unknown error';
      setTestMessage(`Connection failed (${msg}). Check your Render URL.`);
    }
  };

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
        if (err.message.includes('Network Error')) {
          setServerError(
            `Network Error: Unable to connect to backend at ${getBackendBaseUrl()}. Tap "Server Settings" below to configure your live Render URL.`
          );
        } else {
          setServerError(err.message);
        }
      } else {
        setServerError('An unexpected error occurred. Please try again.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-sky-950 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-3xl shadow-2xl p-8 border border-slate-100 relative">
          {/* Top Server Config Gear */}
          <button
            type="button"
            onClick={() => {
              setServerUrlInput(getBackendBaseUrl());
              setTestState('idle');
              setTestMessage('');
              setShowConfigModal(true);
            }}
            className="absolute top-6 right-6 p-2 text-slate-400 hover:text-primary-600 hover:bg-slate-50 rounded-xl transition-colors"
            title="Configure Backend Server URL"
          >
            <Settings size={20} />
          </button>

          {/* Logo & Heading */}
          <div className="flex flex-col items-center mb-6">
            <div className="w-16 h-16 bg-gradient-to-tr from-primary-600 to-sky-500 rounded-2xl flex items-center justify-center mb-3 shadow-lg shadow-sky-500/20">
              <Bus size={32} className="text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">SmartBus Admin</h1>
            <p className="text-sm text-gray-500 mt-1">Fleet Management & Operator Portal</p>
          </div>

          {/* Connected backend pill */}
          <div className="mb-5 flex items-center justify-between px-3.5 py-2 bg-slate-50 border border-slate-200/60 rounded-xl text-xs text-slate-600">
            <span className="flex items-center gap-1.5 font-medium truncate max-w-[240px]">
              <Globe size={14} className="text-sky-600 flex-shrink-0" />
              <span className="truncate">{getBackendBaseUrl()}</span>
            </span>
            <button
              type="button"
              onClick={() => setShowConfigModal(true)}
              className="text-primary-600 hover:text-primary-700 font-semibold flex-shrink-0 ml-2"
            >
              Change
            </button>
          </div>

          {/* Quick Demo Fill Buttons */}
          <div className="mb-5 flex gap-2">
            <button
              type="button"
              onClick={() => {
                setValue('email', 'admin@smartbus.local');
                setValue('password', 'Admin@123456');
              }}
              className="flex-1 py-1 px-2 text-xs bg-sky-50 text-sky-700 font-medium rounded-lg hover:bg-sky-100 transition-colors"
            >
              Fill Admin
            </button>
            <button
              type="button"
              onClick={() => {
                setValue('email', 'owner@smartbus.local');
                setValue('password', 'Owner@123456');
              }}
              className="flex-1 py-1 px-2 text-xs bg-slate-100 text-slate-700 font-medium rounded-lg hover:bg-slate-200 transition-colors"
            >
              Fill Bus Owner
            </button>
          </div>

          {/* Error alert */}
          {serverError && (
            <div className="mb-5 flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
              <AlertCircle size={18} className="text-red-500 flex-shrink-0 mt-0.5" />
              <div className="text-xs text-red-700 leading-relaxed space-y-1">
                <p className="font-semibold">Connection Issue</p>
                <p>{serverError}</p>
              </div>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <Input
              label="Email address"
              type="email"
              placeholder="admin@smartbus.local"
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
              className="w-full mt-2 shadow-lg shadow-primary-600/25"
            >
              Sign In to Dashboard
            </Button>
          </form>

          <p className="mt-6 text-center text-xs text-gray-400">
            Protected Transit Portal • Admin & Fleet Operator Access
          </p>
        </div>
      </div>

      {/* Backend Server Configuration Modal */}
      <Modal
        isOpen={showConfigModal}
        onClose={() => setShowConfigModal(false)}
        title="Backend Server Configuration"
      >
        <div className="space-y-4">
          <p className="text-xs text-gray-500">
            Configure the API server that this Web Admin dashboard communicates with. You can use your live 24/7 Render cloud backend or local development server.
          </p>

          <div className="space-y-2">
            <label className="block text-xs font-semibold text-gray-700">Quick Presets:</label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setServerUrlInput('https://smart-bus-backend-two.vercel.app/api/v1')}
                className="p-2.5 text-left border border-sky-200 bg-sky-50 rounded-xl hover:bg-sky-100 text-xs transition-colors"
              >
                <p className="font-bold text-sky-900 flex items-center gap-1.5">
                  <Server size={14} className="text-sky-600" />
                  Vercel Backend (24/7)
                </p>
                <p className="text-[10px] text-sky-700 truncate mt-0.5">https://smart-bus-backend-two.vercel.app/api/v1</p>
              </button>
              <button
                type="button"
                onClick={() => setServerUrlInput('http://localhost:3000/api/v1')}
                className="p-2.5 text-left border border-slate-200 bg-slate-50 rounded-xl hover:bg-slate-100 text-xs transition-colors"
              >
                <p className="font-bold text-slate-800 flex items-center gap-1.5">
                  <Globe size={14} className="text-slate-600" />
                  Localhost (Dev)
                </p>
                <p className="text-[10px] text-slate-600 truncate mt-0.5">http://localhost:3000/api/v1</p>
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Custom API Base URL:</label>
            <input
              type="text"
              value={serverUrlInput}
              onChange={(e) => setServerUrlInput(e.target.value)}
              placeholder="https://your-backend.onrender.com/api/v1"
              className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:ring-2 focus:ring-primary-500 focus:outline-none"
            />
          </div>

          {testMessage && (
            <div
              className={`p-3 rounded-xl text-xs flex items-center gap-2 ${
                testState === 'success'
                  ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                  : testState === 'error'
                  ? 'bg-rose-50 text-rose-800 border border-rose-200'
                  : 'bg-blue-50 text-blue-800 border border-blue-200'
              }`}
            >
              {testState === 'success' && <CheckCircle2 size={16} className="text-emerald-600 flex-shrink-0" />}
              {testState === 'error' && <AlertCircle size={16} className="text-rose-600 flex-shrink-0" />}
              {testState === 'testing' && <div className="w-4 h-4 border-2 border-blue-600 border-t-transparent rounded-full animate-spin flex-shrink-0" />}
              <span className="truncate">{testMessage}</span>
            </div>
          )}

          <div className="flex gap-2 justify-end pt-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => setShowConfigModal(false)}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              loading={testState === 'testing'}
              onClick={() => handleTestAndSave(serverUrlInput)}
            >
              Test & Save URL
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default Login;
