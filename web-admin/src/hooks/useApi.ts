import {
  useQuery as useReactQuery,
  useMutation as useReactMutation,
  UseQueryOptions,
  UseMutationOptions,
} from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { apiClient } from '../lib/axios';
import { ApiResponse } from '../types';

// Generic API fetch function
export const apiFetch = async <T>(url: string, params?: Record<string, unknown>): Promise<T> => {
  const response = await apiClient.get<ApiResponse<T>>(url, { params });
  if (!response.data.success) {
    throw new Error(response.data.error?.message || 'API Error');
  }
  return response.data.data as T;
};

// Generic useQuery wrapper
export function useApiQuery<T>(
  queryKey: unknown[],
  url: string,
  params?: Record<string, unknown>,
  options?: Omit<UseQueryOptions<T, AxiosError>, 'queryKey' | 'queryFn'>
) {
  return useReactQuery<T, AxiosError>({
    queryKey,
    queryFn: () => apiFetch<T>(url, params),
    ...options,
  });
}

// Generic useMutation wrapper
export function useApiMutation<TData, TVariables>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options?: Omit<UseMutationOptions<TData, AxiosError, TVariables>, 'mutationFn'>
) {
  return useReactMutation<TData, AxiosError, TVariables>({
    mutationFn,
    ...options,
  });
}

// POST helper
export async function apiPost<T, B = unknown>(url: string, body: B): Promise<T> {
  const response = await apiClient.post<ApiResponse<T>>(url, body);
  if (!response.data.success) {
    throw new Error(response.data.error?.message || 'API Error');
  }
  return response.data.data as T;
}

// PUT helper
export async function apiPut<T, B = unknown>(url: string, body: B): Promise<T> {
  const response = await apiClient.put<ApiResponse<T>>(url, body);
  if (!response.data.success) {
    throw new Error(response.data.error?.message || 'API Error');
  }
  return response.data.data as T;
}

// DELETE helper
export async function apiDelete<T>(url: string): Promise<T> {
  const response = await apiClient.delete<ApiResponse<T>>(url);
  if (!response.data.success) {
    throw new Error(response.data.error?.message || 'API Error');
  }
  return response.data.data as T;
}
