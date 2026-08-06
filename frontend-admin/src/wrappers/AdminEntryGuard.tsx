import { useAccess } from '@umijs/max';
import PermissionGuard from './PermissionGuard';

/**
 * B 端入口级路由守卫。
 *
 * 用于 /admin 总入口：只有具备基础准入权限（任意一个 B 端角色）的身份才能进入运营后台。
 * 未认证（无 B 端角色）时引导到登录页，而非 403——403 留给已登录但角色不足的细粒度守卫。
 * 它是粗粒度闸门，进入后再由各页面守卫（OperatorGuard、DemoTasksGuard 等）做细粒度校验。
 */
export default function AdminEntryGuard() {
  const access = useAccess();

  return <PermissionGuard allowed={access.canEnterAdmin} redirectTo="/admin/login" />;
}
