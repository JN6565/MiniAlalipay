/**
 * B 端界面权限模型。
 *
 * 职责：根据 Umi initialState 中的服务端身份角色，计算“界面可见性”与“操作能力”，
 * 供路由守卫、侧边菜单和页面按钮统一消费。组件一律消费这里的布尔权限，
 * 禁止直接判断角色字符串，避免权限规则散落各处。
 *
 * 重要边界：这里的权限只控制界面，服务端仍是最终授权方；
 * 前端权限不能替代网关和后端的身份认证、角色授权及数据范围校验。
 */

/**
 * 角色集合含普通用户与 B 端运营/管理员，用于计算界面可见性和操作能力，服务端仍是最终授权方。
 *
 * - `USER`：普通用户（C 端），无 B 端准入权限，是 /api/v1/auth/me 的默认角色。
 * - `OPERATOR`：运营人员，可查看与处置人工工单、告警，并触发演示任务。
 * - `ADMIN`：管理员，具备运营人员全部能力，并可配置非资金告警阈值和管理 B 端用户。
 */
export type AdminRole = 'USER' | 'OPERATOR' | 'ADMIN';

/** 当前 B 端身份的最小客户端视图，正式字段应在身份契约落地后由 OpenAPI 生成。 */
export interface AdminIdentity {
  /** 运营用户 ID（来自网关 /auth/me），用于识别工单领取人是否当前操作者。 */
  userId: string;
  /** 脱敏展示名称。 */
  displayName: string;
  /** 服务端授予的 B 端角色；多角色身份按权限并集计算。 */
  roles: AdminRole[];
}

/**
 * B 端界面权限集合。
 *
 * 查看权限与操作权限分别声明，供路由守卫、菜单和页面操作统一消费；
 * 组件不得绕过该模型直接判断角色字符串。
 */
export interface AdminAccess {
  /** 是否允许进入 B 端。 */
  canEnterAdmin: boolean;
  /** 是否允许查看可信运行看板。 */
  canViewDashboard: boolean;
  /** 是否允许查看人工确认台。 */
  canViewManualCases: boolean;
  /** 是否允许处置人工工单。 */
  canOperateManualCases: boolean;
  /** 是否允许查看 T+1 报表。 */
  canViewReports: boolean;
  /** 是否允许查看告警中心。 */
  canViewAlerts: boolean;
  /** 是否允许处置告警。 */
  canOperateAlerts: boolean;
  /** 是否允许配置非资金告警阈值。 */
  canConfigureAlertThresholds: boolean;
  /** 是否允许查看数据质量页面。 */
  canViewDataQuality: boolean;
  /** 是否允许查看交易查询与回执页面。 */
  canViewTransactions: boolean;
  /** 是否允许查看链路追溯页面。 */
  canViewTrace: boolean;
  /** 是否允许触发受审计的演示任务。 */
  canRunDemoTasks: boolean;
  /** 是否允许管理 B 端用户。 */
  canManageUsers: boolean;
}

/** 根据服务端授予的角色计算 B 端界面权限。 */
export default function access(initialState?: { currentAdmin?: AdminIdentity }): AdminAccess {
  const roles = new Set(initialState?.currentAdmin?.roles ?? []);
  const isOperator = roles.has('OPERATOR');
  const isAdmin = roles.has('ADMIN');
  const canEnterAdmin = isOperator || isAdmin;

  return {
    canEnterAdmin,
    canViewDashboard: canEnterAdmin,
    canViewManualCases: isOperator || isAdmin,
    canOperateManualCases: isOperator || isAdmin,
    canViewReports: canEnterAdmin,
    canViewAlerts: canEnterAdmin,
    canOperateAlerts: isOperator || isAdmin,
    canConfigureAlertThresholds: isAdmin,
    canViewDataQuality: canEnterAdmin,
    canViewTransactions: canEnterAdmin,
    canViewTrace: canEnterAdmin,
    canRunDemoTasks: isAdmin,
    canManageUsers: isAdmin,
  };
}
