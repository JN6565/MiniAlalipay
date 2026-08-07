import {
  AlertOutlined,
  AuditOutlined,
  BarChartOutlined,
  BugOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ReconciliationOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';
import type { AdminAccess } from '@/access';

/**
 * B 端导航菜单构建。
 *
 * 每项菜单声明其查看所需的最小权限，buildAdminMenuItems 根据统一的 access 模型
 * 过滤出当前身份可见的菜单。菜单隐藏只是界面层措施，不替代路由守卫与服务端鉴权。
 */

/** B 端导航菜单项。 */
export interface AdminMenuItem {
  /** 对应的 B 端路由。 */
  key: string;
  /** 菜单图标。 */
  icon: ReactNode;
  /** 中文菜单名称。 */
  label: string;
}

type AdminMenuPermission = keyof Pick<
  AdminAccess,
  | 'canViewDashboard'
  | 'canViewManualCases'
  | 'canViewReports'
  | 'canViewAlerts'
  | 'canViewDataQuality'
  | 'canViewTransactions'
  | 'canViewTrace'
  | 'canConfigureAlertThresholds'
  | 'canRunDemoTasks'
  | 'canManageUsers'
>;

interface AdminMenuDefinition extends AdminMenuItem {
  /** 展示当前菜单项所需的界面权限。 */
  permission: AdminMenuPermission;
}

const adminMenuDefinitions: readonly AdminMenuDefinition[] = [
  {
    key: '/admin/dashboard',
    icon: <DashboardOutlined />,
    label: '可信运行看板',
    permission: 'canViewDashboard',
  },
  {
    key: '/admin/manual-cases',
    icon: <AuditOutlined />,
    label: '人工确认台',
    permission: 'canViewManualCases',
  },
  {
    key: '/admin/alerts',
    icon: <AlertOutlined />,
    label: '告警中心',
    permission: 'canViewAlerts',
  },
  {
    key: '/admin/data-quality',
    icon: <DatabaseOutlined />,
    label: '数据质量',
    permission: 'canViewDataQuality',
  },
  {
    key: '/admin/reports',
    icon: <BarChartOutlined />,
    label: 'T+1 报表',
    permission: 'canViewReports',
  },
  {
    key: '/admin/transactions',
    icon: <ReconciliationOutlined />,
    label: '交易查询与回执',
    permission: 'canViewTransactions',
  },
  {
    key: '/admin/trace',
    icon: <BugOutlined />,
    label: '链路追溯',
    permission: 'canViewTrace',
  },
  {
    key: '/admin/alert-rules',
    icon: <AlertOutlined />,
    label: '告警规则配置',
    permission: 'canConfigureAlertThresholds',
  },
  {
    key: '/admin/demo-tasks',
    icon: <SafetyCertificateOutlined />,
    label: '演示任务触发',
    permission: 'canRunDemoTasks',
  },
  {
    key: '/admin/users',
    icon: <SafetyCertificateOutlined />,
    label: '用户管理',
    permission: 'canManageUsers',
  },
];

/**
 * 根据统一权限模型构建 B 端菜单。
 *
 * 菜单可见性只反映界面权限；用户管理仅系统管理员可见，服务端角色门禁仍是最终授权方。
 */
export function buildAdminMenuItems(access: AdminAccess): AdminMenuItem[] {
  return adminMenuDefinitions
    .filter(({ permission }) => access[permission])
    .map(({ permission: _permission, ...menuItem }) => menuItem);
}

/**
 * 按权限模型过滤带分组的菜单定义。
 *
 * 布局渲染前先调用 buildAdminMenuItems 得到当前身份可见的菜单 key 集合，再保留分组中
 * 可见的子项；某分组子项全部不可见时整体隐藏。隐藏菜单只是界面层措施，不替代路由守卫。
 */
export function filterGroupedMenuByAccess(
  groups: NonNullable<MenuProps['items']>,
  access: AdminAccess,
): NonNullable<MenuProps['items']> {
  const visibleKeys = new Set(buildAdminMenuItems(access).map((item) => item.key));
  return groups
    .map((group) => {
      if (!group) return null;
      if (!('children' in group) || !group.children) return group;
      const children = group.children.filter(
        (child): child is NonNullable<(typeof group.children)[number]> =>
          child !== null && 'key' in child && visibleKeys.has(String(child.key)),
      );
      return children.length > 0 ? { ...group, children } : null;
    })
    .filter((item): item is NonNullable<NonNullable<MenuProps['items']>[number]> => item !== null);
}
