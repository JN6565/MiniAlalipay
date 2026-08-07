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
    });
  });

  it('运营人员可以查看公共页面并处置工单、告警，但不能触发演示任务', () => {
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
    });
  });

  it('管理员拥有运营人员全部能力并可配置告警与管理系统用户', () => {
    const permissions = access({
      currentAdmin: { displayName: '管理员', roles: ['ADMIN'] },
    });

    expect(permissions).toEqual({
      canEnterAdmin: true,
      canViewDashboard: true,
      canViewManualCases: true,
      canOperateManualCases: true,
      canViewReports: true,
      canViewAlerts: true,
      canOperateAlerts: true,
      canConfigureAlertThresholds: true,
      canViewDataQuality: true,
      canViewTransactions: true,
      canViewTrace: true,
      canRunDemoTasks: true,
      canManageUsers: true,
    });
  });

  it('多角色身份按权限并集计算', () => {
    const permissions = access({
      currentAdmin: {
        displayName: '复合角色管理员',
        roles: ['OPERATOR', 'ADMIN'],
      },
    });

    expect(permissions.canEnterAdmin).toBe(true);
    expect(permissions.canViewManualCases).toBe(true);
    expect(permissions.canOperateManualCases).toBe(true);
    expect(permissions.canOperateAlerts).toBe(true);
    expect(permissions.canConfigureAlertThresholds).toBe(true);
    expect(permissions.canRunDemoTasks).toBe(true);
    expect(permissions.canManageUsers).toBe(true);
  });
});
