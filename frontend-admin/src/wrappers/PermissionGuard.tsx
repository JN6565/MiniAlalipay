import { history, Outlet, useModel } from '@umijs/max';
import { Spin } from 'antd';
import { useLayoutEffect } from 'react';

export interface PermissionGuardProps {
  /** 当前身份是否拥有目标页面的查看权限。 */
  allowed: boolean;
}

/**
 * B 端通用路由权限守卫（其余业务守卫均基于它封装）。
 *
 * Umi v4 路由 wrapper 中，子路由页面通过 {@link Outlet} 渲染，因此本守卫不再接收 children，
 * 而是在通过权限后直接渲染 Outlet 指向的页面。
 *
 * 工作方式：先等待 Umi initialState 中的运营身份完成异步加载（避免在身份就绪前把已授权用户误判为无权限
 * 而跳转 403），再在布局渲染阶段（useLayoutEffect，先于 paint）检查布尔权限：
 * 未通过时用 history.replace 跳转到 403 页并返回 null 不渲染子页面。
 */
export default function PermissionGuard({ allowed }: PermissionGuardProps) {
  // 身份仍由 getInitialState 异步加载时不得判定未授权，先渲染加载态避免误跳 403。
  const { loading } = useModel('@@initialState');
  const identityLoaded = loading === false;

  useLayoutEffect(() => {
    if (identityLoaded && !allowed) {
      history.replace('/admin/403');
    }
  }, [allowed, identityLoaded]);

  // 身份未就绪：不渲染子页面也不跳转，展示加载态。
  if (!identityLoaded) {
    return <Spin style={{ display: 'block', margin: '80px auto' }} />;
  }

  // 未授权时不渲染子路由内容，避免页面代码在无权限场景下被执行。
  if (!allowed) {
    return null;
  }

  return <Outlet />;
}
