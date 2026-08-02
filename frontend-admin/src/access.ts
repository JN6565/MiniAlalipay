/** B 端登录角色，用于界面可见性控制，服务端仍是最终授权方。 */
export type AdminRole =
  | 'OPERATOR'
  | 'ADMIN'
  | 'OBSERVER'
  | 'DEMO_ADMIN';

/** 当前 B 端身份的最小客户端视图，正式字段应在身份契约落地后由 OpenAPI 生成。 */
export interface AdminIdentity {
  /** 脱敏展示名称。 */
  displayName: string;
  /** 服务端授予的 B 端角色。 */
  roles: AdminRole[];
}

export default function access(initialState?: { currentAdmin?: AdminIdentity }) {
  const roles = initialState?.currentAdmin?.roles ?? [];

  return {
    canEnterAdmin: roles.some((role) =>
      ['OPERATOR', 'ADMIN', 'OBSERVER', 'DEMO_ADMIN'].includes(role),
    ),
    canOperateManualCases: roles.includes('OPERATOR'),
    canOperateAlerts: roles.includes('OPERATOR'),
    canManageSystem: roles.includes('ADMIN'),
    canRunDemoTasks: roles.includes('DEMO_ADMIN'),
    isReadOnlyObserver: roles.includes('OBSERVER'),
  };
}
