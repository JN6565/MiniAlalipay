import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd-mobile';
import React from 'react';
import NetworkStatusNotice from '@/components/NetworkStatusNotice';
import { h5ThemeVars } from '@/theme';

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

// 根布局：QueryClient 提供数据缓存，ConfigProvider 注入「电光蓝」设计令牌（与 overrides.css 的 --h5-* 同源）
export function rootContainer(container: React.ReactNode) {
  const app = React.createElement(
    React.Fragment,
    null,
    container,
    React.createElement(NetworkStatusNotice),
  );
  // JSX 写法规避 createElement 对 ConfigProvider props 泛型的重载推断失败
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={h5ThemeVars}>{app}</ConfigProvider>
    </QueryClientProvider>
  );
}
