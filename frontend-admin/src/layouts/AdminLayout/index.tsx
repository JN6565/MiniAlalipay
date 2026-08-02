import {
  AlertOutlined,
  AuditOutlined,
  BarChartOutlined,
  BugOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ReconciliationOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { history, Outlet, useLocation } from '@umijs/max';
import { Avatar, Button, Layout, Menu, Tag, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import RouteErrorBoundary from '@/components/RouteErrorBoundary';
import { useAdminUiStore } from '@/stores/ui';
import styles from './index.less';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '可信运行看板' },
  { key: '/admin/manual-cases', icon: <AuditOutlined />, label: '人工确认台' },
  { key: '/admin/alerts', icon: <AlertOutlined />, label: '告警中心' },
  { key: '/admin/data-quality', icon: <DatabaseOutlined />, label: '数据质量' },
  { key: '/admin/reports', icon: <BarChartOutlined />, label: 'T+1 报表' },
  { key: '/admin/transactions', icon: <ReconciliationOutlined />, label: '交易查询与回执' },
  { key: '/admin/trace', icon: <BugOutlined />, label: '链路追溯' },
  { key: '/admin/demo-tasks', icon: <SafetyCertificateOutlined />, label: '演示任务触发' },
] satisfies MenuProps['items'];

export default function AdminLayout() {
  const location = useLocation();
  const menuCollapsed = useAdminUiStore((state) => state.menuCollapsed);
  const toggleMenu = useAdminUiStore((state) => state.toggleMenu);
  const setMenuCollapsed = useAdminUiStore((state) => state.setMenuCollapsed);

  return (
    <Layout className={styles.shell}>
      <Sider
        className={styles.sider}
        collapsed={menuCollapsed}
        collapsedWidth={72}
        width={232}
        breakpoint="lg"
        onBreakpoint={(broken) => setMenuCollapsed(broken)}
        trigger={null}
      >
        <div className={styles.brand}>
          <div className={styles.brandMark}>M</div>
          {!menuCollapsed && (
            <div className={styles.brandText}>
              <strong>MiniAlalipay</strong>
              <span>运营中心</span>
            </div>
          )}
        </div>
        <Menu
          className={styles.menu}
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => history.push(key)}
        />
        {!menuCollapsed && (
          <div className={styles.environment}>
            <span>当前环境</span>
            <Tag color="cyan">本地演示</Tag>
          </div>
        )}
      </Sider>

      <Layout>
        <Header className={styles.header}>
          <Tooltip title={menuCollapsed ? '展开导航' : '收起导航'}>
            <Button
              aria-label={menuCollapsed ? '展开导航' : '收起导航'}
              type="text"
              icon={menuCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={toggleMenu}
            />
          </Tooltip>
          <Avatar size="small" className={styles.headerAvatar}>
            运
          </Avatar>
        </Header>
        <Content className={styles.content}>
          <RouteErrorBoundary>
            <Outlet />
          </RouteErrorBoundary>
        </Content>
      </Layout>
    </Layout>
  );
}
