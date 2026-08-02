import access from './access';

describe('B 端界面权限', () => {
  it('观察者只能以只读方式进入后台', () => {
    const permissions = access({
      currentAdmin: { displayName: '观察者', roles: ['OBSERVER'] },
    });

    expect(permissions.canEnterAdmin).toBe(true);
    expect(permissions.isReadOnlyObserver).toBe(true);
    expect(permissions.canOperateAlerts).toBe(false);
    expect(permissions.canOperateManualCases).toBe(false);
  });

  it('普通未授权身份不能进入后台', () => {
    expect(access().canEnterAdmin).toBe(false);
  });

  it('运营人员可以处置工单和告警，但不能管理系统', () => {
    const permissions = access({
      currentAdmin: { displayName: '运营人员', roles: ['OPERATOR'] },
    });

    expect(permissions.canOperateManualCases).toBe(true);
    expect(permissions.canOperateAlerts).toBe(true);
    expect(permissions.canManageSystem).toBe(false);
  });
});
