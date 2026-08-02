import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import type { AdminIdentity } from './access';
import './global.less';
import { adminTheme } from './theme';

/** B 端运行时初始状态；正式身份字段需等待登录 OpenAPI 契约。 */
export interface AdminInitialState {
  /** 当前运营身份，未接入登录接口时为空。 */
  currentAdmin?: AdminIdentity;
}

/**
 * 初始化 Umi 权限插件依赖的状态模型。
 *
 * 当前 OpenAPI 尚无 B 端登录及当前身份接口，因此只返回空状态，避免在前端伪造角色。
 */
export async function getInitialState(): Promise<AdminInitialState> {
  return {};
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});

export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider locale={zhCN} theme={adminTheme}>
      <App>
        <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}
