import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { getBackendBaseUrl, API_ENDPOINTS } from '../config/api';

const apiClient = axios.create({
  baseURL: getBackendBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: string) => void;
  reject: (reason: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token!);
    }
  });
  failedQueue = [];
};

// Request interceptor: attach Bearer token and current dynamic baseURL
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    config.baseURL = getBackendBaseUrl();
    const stored = localStorage.getItem('smartbus-auth');
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        const token = parsed?.state?.accessToken;
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
      } catch {
        // ignore parse errors
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: handle 401 with token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const stored = localStorage.getItem('smartbus-auth');
        let refreshToken: string | null = null;
        if (stored) {
          const parsed = JSON.parse(stored);
          refreshToken = parsed?.state?.refreshToken;
        }

        if (!refreshToken) throw new Error('No refresh token');

        const response = await axios.post(`${getBackendBaseUrl()}${API_ENDPOINTS.AUTH.REFRESH}`, {
          refreshToken,
        });

        const tokenData = response.data?.data?.tokens || response.data?.data;
        const newAccessToken: string = tokenData?.accessToken;
        const newRefreshToken: string | undefined = tokenData?.refreshToken;

        // Update stored token
        if (stored) {
          const parsed = JSON.parse(stored);
          parsed.state.accessToken = newAccessToken;
          if (newRefreshToken) {
            parsed.state.refreshToken = newRefreshToken;
          }
          localStorage.setItem('smartbus-auth', JSON.stringify(parsed));
        }

        processQueue(null, newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        // Clear auth state
        localStorage.removeItem('smartbus-auth');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export { apiClient };
export default apiClient;
