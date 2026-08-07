import { useAccess } from '@umijs/max';
import PermissionGuard from './PermissionGuard';

/**
 * 人工确认台页面守卫。
 *
 * 人工确认台包含处置资金相关工单的入口（查看与处置同权限），
 * 仅开放给管理员与运营人员（canViewManualCases），普通用户不可见。
 */
export default function OperatorGuard() {
  const access = useAccess();

  return <PermissionGuard allowed={access.canViewManualCases} />;
}
