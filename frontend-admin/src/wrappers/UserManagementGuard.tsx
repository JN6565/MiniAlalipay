import { useAccess } from '@umijs/max';
import PermissionGuard from './PermissionGuard';

/**
 * 用户管理页面守卫。
 *
 * 用户管理属系统管理能力，仅开放给管理员（canManageUsers）。
 * 注意：用户管理接口尚未进入正式 OpenAPI，菜单也不会生成入口（见 AdminLayout/menu.tsx），
 * 该守卫是为契约落地后的路由接入做准备。
 */
export default function UserManagementGuard() {
  const access = useAccess();

  return <PermissionGuard allowed={access.canManageUsers} />;
}
