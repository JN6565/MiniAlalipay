import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// 创建 QueryClient 实例
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      cacheTime: 5 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: 0,
    },
  },
});

// 根布局
export function rootContainer(container: React.ReactNode) {
  return React.createElement(
    QueryClientProvider,
    { client: queryClient },
    container,
  );
}
