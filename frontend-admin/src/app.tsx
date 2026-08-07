import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import { getCurrentIdentity } from '@/services/auth';
import { DEV_STUB_TOKEN, readStoredToken, setActiveToken } from '@/utils/adminToken';
import type { AdminIdentity, AdminRole } from './access';
import './global.less';
import { adminTheme } from './theme';

/**
 * Umi 运行时入口（app.tsx）。
 *
 * 集中完成三件事：
 * 1. 为 Umi access 插件提供初始化状态（当前运营身份，登录后由 /api/v1/auth/me 填充）；
 * 2. 配置 TanStack Query 的全局默认行为；
 * 3. 通过 rootContainer 包裹全局 Provider（主题、Antd App 上下文、QueryClient）。
 *
 * 身份来源：优先持久化登录令牌换取的真实身份；开发环境未登录时注入网关 dev Stub 令牌，
 * 由网关启用 stub 后返回受控身份；身份必须由网关认证后下发，未认证时交由路由守卫引导登录。
 */

/** B 端运行时初始状态。 */
export interface AdminInitialState {
  /** 当前运营身份，未认证时为空。 */
  currentAdmin?: AdminIdentity;
}

/** 服务端可能下发的 B 端角色白名单。 */
const KNOWN_ROLES: readonly AdminRole[] = ['USER', 'OPERATOR', 'ADMIN'];

/**
 * 初始化 Umi 权限插件依赖的状态模型。
 *
 * 优先级：
 * 1. 持久化的真实登录令牌 → 调用 /api/v1/auth/me 换取服务端身份与角色；
 * 2. 开发环境未登录 → 注入网关 dev Stub 令牌；网关启用 stub 时由 /api/v1/auth/me 下发受控身份；
 * 3. 其余未认证场景 → 返回空身份，由 AdminEntryGuard 引导到登录页。
 */
export async function getInitialState(): Promise<AdminInitialState> {
  // 1. 确定生效令牌：优先真实登录令牌；开发环境未登录时注入网关 dev Stub 令牌。
  const storedToken = readStoredToken();
  const activeToken = storedToken ?? (process.env.NODE_ENV === 'development' ? DEV_STUB_TOKEN : null);
  if (activeToken) {
    setActiveToken(activeToken);
  }

  // 2. 有令牌则向网关查询当前身份（真实角色）；会话失效由请求层清理令牌并引导登录。
  if (activeToken) {
    try {
      const me = await getCurrentIdentity();
      const roles = me.data.roles.filter(
        (role): role is AdminRole => KNOWN_ROLES.includes(role as AdminRole),
      );
      return {
        currentAdmin: {
          displayName: me.data.displayName,
          roles,
        },
      };
    } catch {
      // 会话失效或 dev Stub 未启用：身份必须由网关认证后下发，这里不得授予本地身份。
    }
  }

  // 3. 未认证时返回空身份：不允许客户端伪造身份，AdminEntryGuard 将引导到登录页。
  //    dev Stub 只有在网关启用（GATEWAY_AUTH_STUB_ENABLED=true）时才通过上方 /auth/me 下发身份。
  return {};
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
