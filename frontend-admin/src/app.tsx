import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import type { AdminIdentity } from './access';
import './global.less';
import { adminTheme } from './theme';

/**
 * Umi 运行时入口（app.tsx）。
 *
 * 集中完成三件事：
 * 1. 为 Umi access 插件提供初始化状态（当前运营身份）；
 * 2. 配置 TanStack Query 的全局默认行为；
 * 3. 通过 rootContainer 包裹全局 Provider（主题、Antd App 上下文、QueryClient）。
 *
 * 身份字段待登录 OpenAPI 契约落地后由登录/当前身份接口填充。
 */

/** B 端运行时初始状态；正式身份字段需等待登录 OpenAPI 契约。 */
export interface AdminInitialState {
  /** 当前运营身份，未接入登录接口时为空。 */
  currentAdmin?: AdminIdentity;
}

/**
 * 初始化 Umi 权限插件依赖的状态模型。
 *
 * 当前 OpenAPI 尚无 B 端登录及当前身份接口，因此本地演示返回开发态 ADMIN 身份，
 * 解锁路由守卫与页面按钮；网关 dev 鉴权桩需同步配置 ADMIN 角色（GATEWAY_AUTH_STUB_ROLES=ADMIN）。
 * 身份契约落地后，应改为调用网关的当前身份接口并按服务端返回填充 currentAdmin。
 */
export async function getInitialState(): Promise<AdminInitialState> {
  return {
    currentAdmin: { displayName: '开发运营', roles: ['ADMIN'] },
  };
}

// 全局 QueryClient：管理服务端查询的缓存、失效与重试策略。
// 选择说明——读取类请求允许 1 次自动重试以容忍网关瞬时抖动；
// 写操作（mutation）不自动重试，避免重复提交产生副作用（服务端幂等键只保证业务幂等，不替代前端防重）。
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 30 秒内视为新鲜，期间不重复请求，降低运营后台高频操作的接口压力。
      staleTime: 30_000,
      // 无引用 5 分钟后回收缓存，避免长期驻留无用数据。
      gcTime: 5 * 60_000,
      retry: 1,
      // 不因窗口重新聚焦而刷新，避免打断运营正在查看的上下文。
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});

/**
 * 全局容器包裹层。
 *
 * 自内向外依次提供：TanStack Query 服务端缓存上下文、Antd App 上下文
 * （供 message/notification/modal 静态方法统一消费主题与国际化）、
 * 中文 locale 与「晴空」主题。包裹顺序保证内部组件能同时拿到查询能力与主题上下文。
 */
export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider locale={zhCN} theme={adminTheme}>
      <App>
        <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}
