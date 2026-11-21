export const getBackendBaseUrl = (): string => {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('smartbus_api_base_url');
    if (saved && saved.trim()) return saved.trim();
  }
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (
    typeof window !== 'undefined' &&
    window.location.hostname &&
    window.location.hostname !== 'localhost' &&
    window.location.hostname !== '127.0.0.1'
  ) {
    return 'https://smart-bus-system.onrender.com/api/v1';
  }
  return 'http://localhost:3000/api/v1';
};

export const getWsUrl = (): string => {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('smartbus_ws_url');
    if (saved && saved.trim()) return saved.trim();
  }
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL;
  }
  if (
    typeof window !== 'undefined' &&
    window.location.hostname &&
    window.location.hostname !== 'localhost' &&
    window.location.hostname !== '127.0.0.1'
  ) {
    return 'https://smart-bus-system.onrender.com';
  }
  return 'http://localhost:3000';
};

export const API_BASE_URL = getBackendBaseUrl();
export const WS_URL = getWsUrl();

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
