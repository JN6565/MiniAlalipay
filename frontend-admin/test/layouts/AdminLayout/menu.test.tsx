import type { MenuProps } from 'antd';
import type { AdminAccess } from '@/access';
import { buildAdminMenuItems, filterGroupedMenuByAccess } from '../../../src/layouts/AdminLayout/menu';

/** 布局中的分组菜单样例（抽样总览/数据两组，用于验证分组过滤逻辑）。 */
const groupedMenus: NonNullable<MenuProps['items']> = [
  {
    type: 'group',
    label: '总览',
    children: [
      { key: '/admin/dashboard', label: '可信运行看板' },
      { key: '/admin/manual-cases', label: '人工确认台' },
    ],
  },
  {
    type: 'group',
    label: '数据',
    children: [{ key: '/admin/data-quality', label: '数据质量' }],
  },
];

/** 提取过滤后分组的可见 key 列表。 */
function groupedKeys(groups: NonNullable<MenuProps['items']>): string[] {
  return groups.flatMap((group) => (group && 'children' in group ? (group.children ?? []) : []))
    .filter((child): child is { key: string } => child !== null && 'key' in child)
    .map((child) => String(child.key));
}

/**
 * B 端权限菜单单元测试。
 *
 * 验证菜单随权限模型正确增删：空权限不生成菜单、公共页面全员可见、
 * 运营人员多出人工确认台、管理员多出演示任务与用户管理，无权限身份不生成用户管理菜单。
 */

/** 全权限关闭的基准身份，供各用例在其上叠加所需权限。 */
const noAccess: AdminAccess = {
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
};

/** 公共运营页面可见的权限组合。 */
const publicPageAccess: AdminAccess = {
  ...noAccess,
  canEnterAdmin: true,
  canViewDashboard: true,
  canViewReports: true,
  canViewAlerts: true,
  canViewDataQuality: true,
  canViewTransactions: true,
  canViewTrace: true,
};

/** 提取构建后菜单的路由 key 列表，便于断言。 */
function menuKeys(access: AdminAccess): string[] {
  return buildAdminMenuItems(access).map(({ key }) => key);
}

describe('B 端权限菜单', () => {
  it('空权限不生成任何菜单', () => {
    expect(buildAdminMenuItems(noAccess)).toEqual([]);
  });

  it('仅具备公共页面权限时只生成公共运营页面菜单', () => {
    expect(
      menuKeys({
        ...publicPageAccess,
        canViewManualCases: false,
      }),
    ).toEqual([
      '/admin/dashboard',
      '/admin/alerts',
      '/admin/data-quality',
      '/admin/reports',
      '/admin/transactions',
      '/admin/trace',
    ]);
  });

  it('运营人员额外获得人工确认台菜单', () => {
    expect(
      menuKeys({
        ...publicPageAccess,
        canViewManualCases: true,
        canOperateManualCases: true,
        canOperateAlerts: true,
      }),
    ).toEqual([
      '/admin/dashboard',
      '/admin/manual-cases',
      '/admin/alerts',
      '/admin/data-quality',
      '/admin/reports',
      '/admin/transactions',
      '/admin/trace',
    ]);
  });

  it('管理员额外获得告警规则配置、演示任务与用户管理菜单', () => {
    expect(
      menuKeys({
        ...publicPageAccess,
        canConfigureAlertThresholds: true,
        canRunDemoTasks: true,
        canManageUsers: true,
      }),
    ).toEqual([
      '/admin/dashboard',
      '/admin/alerts',
      '/admin/data-quality',
      '/admin/reports',
      '/admin/transactions',
      '/admin/trace',
      '/admin/alert-rules',
      '/admin/demo-tasks',
      '/admin/users',
    ]);
  });

  it('无用户管理权限时不生成用户管理菜单', () => {
    const keys = menuKeys({
      ...publicPageAccess,
      canManageUsers: false,
    });

    expect(keys).not.toContain('/admin/users');
  });
});

describe('B 端分组菜单按权限过滤', () => {
  it('仅具备公共页面权限时仅看到公共分组中的可见子项，隐藏无权限的运营菜单', () => {
    const filtered = filterGroupedMenuByAccess(groupedMenus, {
      ...publicPageAccess,
      canViewManualCases: false,
    });

    expect(groupedKeys(filtered)).toEqual(['/admin/dashboard', '/admin/data-quality']);
  });

  it('运营人员额外保留人工确认台子项', () => {
    const filtered = filterGroupedMenuByAccess(groupedMenus, {
      ...publicPageAccess,
      canViewManualCases: true,
      canOperateManualCases: true,
    });

    expect(groupedKeys(filtered)).toEqual([
      '/admin/dashboard',
      '/admin/manual-cases',
      '/admin/data-quality',
    ]);
  });

  it('分组子项全部不可见时整体移除该分组', () => {
    const onlyOperatorMenus: NonNullable<MenuProps['items']> = [
      {
        type: 'group',
        label: '总览',
        children: [{ key: '/admin/manual-cases', label: '人工确认台' }],
      },
    ];

    const filtered = filterGroupedMenuByAccess(onlyOperatorMenus, noAccess);

    expect(groupedKeys(filtered)).toEqual([]);
  });
});
