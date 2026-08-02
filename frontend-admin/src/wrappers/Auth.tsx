import { useAccess, history } from '@umijs/max';
import { useLayoutEffect } from 'react';
import type { ReactNode } from 'react';

export interface AuthWrapperProps {
  children: ReactNode;
}

/**
 * 路由权限守卫，权限不通过时跳转到 /admin/403。
 *
 * 与 routes 中的 access 属性二选一使用：
 * - `access` 适合控制菜单可见性和全局路由过滤，无权限表现为 404；
 * - `wrappers: ['@/wrappers/Auth']` 适合需要明确 403 反馈的路由。
 */
export default function AuthWrapper({ children }: AuthWrapperProps) {
  const access = useAccess();

  useLayoutEffect(() => {
    if (!access.canManageSystem) {
      history.replace('/admin/403');
    }
  }, [access.canManageSystem]);

  if (!access.canManageSystem) {
    return null;
  }

  return <>{children}</>;
}
