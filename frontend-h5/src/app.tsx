import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import NetworkStatusNotice from '@/components/NetworkStatusNotice';

// 创建 QueryClient 实例
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      gcTime: 5 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
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
    React.createElement(
      React.Fragment,
      null,
      container,
      React.createElement(NetworkStatusNotice),
    ),
  );
}
