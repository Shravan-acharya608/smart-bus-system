const getBackendHost = () => {
  if (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
    return `http://${window.location.hostname}:3000`;
  }
  return 'http://localhost:3000';
};

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || `${getBackendHost()}/api/v1`;
export const WS_URL = import.meta.env.VITE_WS_URL || getBackendHost();

export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: '/auth/login',
    LOGOUT: '/auth/logout',
    REFRESH: '/auth/refresh',
    REGISTER: '/auth/register',
  },
  BUSES: '/buses',
  ROUTES: '/routes',
  TRIPS: '/trips',
  TICKETS: '/tickets',
  ADMIN: {
    DASHBOARD: '/admin/dashboard',
    USERS: '/admin/users',
    REPORTS: '/admin/reports',
  },
} as const;
