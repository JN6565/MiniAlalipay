import access from '../src/access';

/**
 * B 端界面权限模型单元测试。
 *
 * 覆盖角色 → 界面权限映射的完整矩阵，作为权限模型的事实基准：
 * 任何角色调整（新增、移除、变更权限映射）都必须同步更新本测试，
 * 防止前端界面权限与服务端授权语义漂移。
 */
describe('B 端界面权限', () => {
  it('空身份不能进入 B 端或访问任何页面和操作', () => {
    expect(access()).toEqual({
      canEnterAdmin: false,
      canViewDashboard: false,
      canViewManualCases: false,
      canOperateManualCases: false,
      canViewReports: false,
      canViewAlerts: false,
      canOperateAlerts: false,
      canConfigureAlertThresholds: false,
      canViewDataQuality: false,
      canViewTransactions: false,
      canViewTrace: false,
      canRunDemoTasks: false,
      canManageUsers: false,
      isReadOnlyObserver: false,
    });
  });

  it('运营人员可以查看公共页面并处置工单和告警', () => {
    const permissions = access({
      currentAdmin: { displayName: '运营人员', roles: ['OPERATOR'] },
    });

    expect(permissions).toEqual({
      canEnterAdmin: true,
      canViewDashboard: true,
      canViewManualCases: true,
      canOperateManualCases: true,
      canViewReports: true,
      canViewAlerts: true,
      canOperateAlerts: true,
      canConfigureAlertThresholds: false,
      canViewDataQuality: true,
      canViewTransactions: true,
      canViewTrace: true,
      canRunDemoTasks: false,
      canManageUsers: false,
      isReadOnlyObserver: false,
    });
  });

  it('观察者只能查看公共运营页面', () => {
    const permissions = access({
      currentAdmin: { displayName: '观察者', roles: ['OBSERVER'] },
    });

    expect(permissions).toEqual({
      canEnterAdmin: true,
      canViewDashboard: true,
      canViewManualCases: false,
      canOperateManualCases: false,
      canViewReports: true,
      canViewAlerts: true,
      canOperateAlerts: false,
      canConfigureAlertThresholds: false,
      canViewDataQuality: true,
      canViewTransactions: true,
      canViewTrace: true,
      canRunDemoTasks: false,
      canManageUsers: false,
      isReadOnlyObserver: true,
    });
  });

  it('管理员可以查看公共页面、配置告警并管理系统能力', () => {
    const permissions = access({
      currentAdmin: { displayName: '管理员', roles: ['ADMIN'] },
    });

    expect(permissions).toEqual({
      canEnterAdmin: true,
      canViewDashboard: true,
      canViewManualCases: false,
      canOperateManualCases: false,
      canViewReports: true,
      canViewAlerts: true,
      canOperateAlerts: false,
      canConfigureAlertThresholds: true,
      canViewDataQuality: true,
      canViewTransactions: true,
      canViewTrace: true,
      canRunDemoTasks: true,
      canManageUsers: true,
      isReadOnlyObserver: false,
    });
  });

  it('多角色身份按权限并集计算且不判定为只读观察者', () => {
    const permissions = access({
      currentAdmin: {
        displayName: '复合角色管理员',
        roles: ['OBSERVER', 'OPERATOR', 'ADMIN'],
      },
    });

    expect(permissions.canEnterAdmin).toBe(true);
    expect(permissions.canViewManualCases).toBe(true);
    expect(permissions.canOperateManualCases).toBe(true);
    expect(permissions.canOperateAlerts).toBe(true);
    expect(permissions.canConfigureAlertThresholds).toBe(true);
    expect(permissions.canRunDemoTasks).toBe(true);
    expect(permissions.canManageUsers).toBe(true);
    expect(permissions.isReadOnlyObserver).toBe(false);
  });
});
