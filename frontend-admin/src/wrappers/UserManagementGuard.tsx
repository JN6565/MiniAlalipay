import { useAccess } from '@umijs/max';
import PermissionGuard from './PermissionGuard';

/**
 * 用户管理页面守卫。
 *
 * 用户管理属系统管理能力，仅开放给管理员（canManageUsers）；菜单入口同权限过滤
 * （见 AdminLayout/menu.tsx），无权限时路由守卫先行拦截，服务端角色门禁为最终授权方。
 */
export default function UserManagementGuard() {
  const access = useAccess();

  return <PermissionGuard allowed={access.canManageUsers} />;
}
