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
 * B 端登录角色，用于计算界面可见性和操作能力，服务端仍是最终授权方。
 *
 * - `OPERATOR`：运营人员，可处理人工工单和告警。
 * - `OBSERVER`：观察者，只读查看公共运营页面。
 * - `ADMIN`：管理员，可管理用户、配置非资金告警阈值并触发演示任务。
 */
export type AdminRole = 'OPERATOR' | 'OBSERVER' | 'ADMIN';

/** 当前 B 端身份的最小客户端视图，正式字段应在身份契约落地后由 OpenAPI 生成。 */
export interface AdminIdentity {
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
  /** 当前身份是否只有观察者权限。 */
  isReadOnlyObserver: boolean;
}

/** 根据服务端授予的角色计算 B 端界面权限。 */
export default function access(initialState?: { currentAdmin?: AdminIdentity }): AdminAccess {
  const roles = new Set(initialState?.currentAdmin?.roles ?? []);
  const isOperator = roles.has('OPERATOR');
  const isObserver = roles.has('OBSERVER');
  const isAdmin = roles.has('ADMIN');
  const canEnterAdmin = isOperator || isObserver || isAdmin;

  return {
    canEnterAdmin,
    canViewDashboard: canEnterAdmin,
    canViewManualCases: isOperator,
    canOperateManualCases: isOperator,
    canViewReports: canEnterAdmin,
    canViewAlerts: canEnterAdmin,
    canOperateAlerts: isOperator,
    canConfigureAlertThresholds: isAdmin,
    canViewDataQuality: canEnterAdmin,
    canViewTransactions: canEnterAdmin,
    canViewTrace: canEnterAdmin,
    canRunDemoTasks: isAdmin,
    canManageUsers: isAdmin,
    isReadOnlyObserver: isObserver && !isOperator && !isAdmin,
  };
}
