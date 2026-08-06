import {
  AlertOutlined,
  AuditOutlined,
  BarChartOutlined,
  BugOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ReconciliationOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { history, Outlet, useAccess, useLocation, useModel } from '@umijs/max';
import { App, Avatar, Button, Dropdown, Layout, Menu, Tag, Tooltip, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AdminAccess } from '@/access';
import RouteErrorBoundary from '@/components/RouteErrorBoundary';
import { adminLogout } from '@/services/auth';
import { useAdminUiStore } from '@/stores/ui';
import { clearToken } from '@/utils/adminToken';
import { filterGroupedMenuByAccess } from './menu';
import styles from './index.less';

const { Header, Sider, Content, Footer } = Layout;

/**
 * 侧边导航菜单项配置（按「总览 / 数据 / 系统」分组，与方案 C 侧栏布局一致）。
 *
 * 注意：本数组保留全部导航项，仅表达分组的视觉层次，子项 key 不变，不影响选中态与点击跳转。
 * 渲染时由 buildAdminMenuItems（menu.tsx）按统一权限模型过滤出当前身份可见的子项，
 * 某分组全部不可见时整体隐藏，避免权限逻辑散落在布局中。
 */
const menuItems: NonNullable<MenuProps['items']> = [
  {
    type: 'group',
    label: '总览',
    children: [
      { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '可信运行看板' },
      { key: '/admin/manual-cases', icon: <AuditOutlined />, label: '人工确认台' },
      { key: '/admin/alerts', icon: <AlertOutlined />, label: '告警中心' },
    ],
  },
  {
    type: 'group',
    label: '数据',
    children: [
      { key: '/admin/data-quality', icon: <DatabaseOutlined />, label: '数据质量' },
      { key: '/admin/reports', icon: <BarChartOutlined />, label: 'T+1 报表' },
      { key: '/admin/transactions', icon: <ReconciliationOutlined />, label: '交易查询与回执' },
    ],
  },
  {
    type: 'group',
    label: '系统',
    children: [
      { key: '/admin/trace', icon: <BugOutlined />, label: '链路追溯' },
      { key: '/admin/alert-rules', icon: <AlertOutlined />, label: '告警规则配置' },
      { key: '/admin/demo-tasks', icon: <SafetyCertificateOutlined />, label: '演示任务触发' },
      { key: '/admin/users', icon: <SafetyCertificateOutlined />, label: '用户管理' },
    ],
  },
];

/**
 * 顶栏展示的页面标题与简短补充说明，按路由映射。
 * 标题需与菜单标签保持一致；说明为页面职责的简洁版，样式对齐方案 C 顶栏 meta
 * （形如「更新于刚刚 · 网关链路正常 · 本地演示骨架」）。
 */
const routeMeta: Record<string, { title: string; meta: string }> = {
  '/admin/dashboard': { title: '可信运行看板', meta: '网关健康 · 业务指标待接入' },
  '/admin/manual-cases': { title: '人工确认台', meta: '异常上下文 · 风险工单处理' },
  '/admin/alerts': { title: '告警中心', meta: '资金一致性 · 事务健康 · 数据告警' },
  '/admin/data-quality': { title: '数据质量', meta: '完整性 · 唯一性 · 合法性 · 及时性' },
  '/admin/reports': { title: 'T+1 报表', meta: '上一自然日指标 · 只读' },
  '/admin/transactions': { title: '交易查询与回执', meta: '服务端唯一事实 · 脱敏回执' },
  '/admin/trace': { title: '链路追溯', meta: 'Agent · 网关 · 风控 · 事务 · 账本' },
  '/admin/alert-rules': { title: '告警规则配置', meta: '阈值 CAS 调整 · 记录操作者' },
  '/admin/demo-tasks': { title: '演示任务触发', meta: '受审计触发 · 不修改金额' },
  '/admin/users': { title: '用户管理', meta: '用户只读列表 · 冻结/解冻二次确认' },
};

/**
 * 运营后台整体布局。
 *
 * 结构：左侧可折叠导航（Sider）+ 顶栏（Header）+ 内容区（Content）+ 底部（Footer）。
 * 职责边界：布局只负责框架与导航，菜单可见性受权限模型控制，
 * 但“隐藏菜单”不能代替服务端鉴权——无权限时仍由路由守卫拦截。
 */
export default function AdminLayout() {
  const location = useLocation();
  // 菜单折叠状态来自 Zustand 客户端 UI store，不包含任何服务端业务事实。
  const menuCollapsed = useAdminUiStore((state) => state.menuCollapsed);
  const toggleMenu = useAdminUiStore((state) => state.toggleMenu);
  const setMenuCollapsed = useAdminUiStore((state) => state.setMenuCollapsed);

  // 当前身份权限由 Umi access 插件注入；隐藏菜单只是界面层措施，不替代路由守卫与服务端鉴权。
  const access = useAccess() as AdminAccess;
  // 按统一权限模型过滤分组菜单（menu.tsx 的 filterGroupedMenuByAccess）。
  const filteredMenuItems = useMemo(() => filterGroupedMenuByAccess(menuItems, access), [access]);

  // 当前运营身份（登录后由 /api/v1/auth/me 填充；开发环境回退 dev Stub 受控身份）。
  const { initialState } = useModel('@@initialState');
  const { message } = App.useApp();
  const admin = initialState?.currentAdmin;
  const displayName = admin?.displayName ?? '运营管理员';
  const roleText = admin?.roles?.includes('ADMIN')
    ? '系统管理员'
    : admin?.roles?.includes('OPERATOR')
      ? '运营人员'
      : '已登录';

  /** 退出登录：销毁服务端会话并清除本地令牌，回到登录页。 */
  const handleLogout = async () => {
    try {
      await adminLogout();
    } catch {
      // 服务端会话可能已失效，本地清理后仍回到登录页。
    }
    clearToken();
    message.success('已退出登录');
    window.location.assign('/admin/login');
  };

  /** 内容区滚动时在头部显示阴影，形成层次感知。 */
  const [scrolled, setScrolled] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  // 监听内容区滚动位置：只要滚过顶部就点亮顶栏阴影，给运营一个“已开始滚动”的视觉提示。
  const handleScroll = useCallback(() => {
    const el = contentRef.current;
    if (el) setScrolled(el.scrollTop > 0);
  }, []);

  useEffect(() => {
    const el = contentRef.current;
    if (!el) return;
    el.addEventListener('scroll', handleScroll, { passive: true });
    // 挂载时立即计算一次，避免刷新后停留在已滚动位置却无阴影。
    handleScroll();
    return () => el.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  // 顶栏标题与补充说明：按路由从 routeMeta 读取。
  const currentTitle = routeMeta[location.pathname]?.title;
  const currentMeta = routeMeta[location.pathname]?.meta;

  return (
    <Layout className={styles.shell}>
      <Sider
        className={styles.sider}
        collapsed={menuCollapsed}
        collapsedWidth={64}
        width={200}
        // 窄屏（小于 lg 断点）自动收起侧栏，并同步写回 Zustand，保证刷新后状态一致。
        breakpoint="lg"
        onBreakpoint={(broken) => setMenuCollapsed(broken)}
        // 使用自定义顶栏按钮控制折叠，隐藏 antd 默认 trigger。
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
          items={filteredMenuItems}
          onClick={({ key }) => history.push(key)}
        />
        {!menuCollapsed && (
          <div className={styles.environment}>
            <span>当前环境</span>
            <Tag color="cyan">本地演示</Tag>
          </div>
        )}
      </Sider>

      <Layout className={styles.main}>
        <Header className={`${styles.header} ${scrolled ? styles.headerScrolled : ''}`}>
          <div className={styles.headerLeft}>
            <Tooltip title={menuCollapsed ? '展开导航' : '收起导航'}>
              <Button
                aria-label={menuCollapsed ? '展开导航' : '收起导航'}
                type="text"
                icon={menuCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={toggleMenu}
              />
            </Tooltip>
            {currentTitle && (
              <div className={styles.headerText}>
                <div className={styles.headerTitle}>{currentTitle}</div>
                {currentMeta && <div className={styles.headerMeta}>{currentMeta}</div>}
              </div>
            )}
          </div>
          <div className={styles.headerUser}>
            <Dropdown
              menu={{
                items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
                onClick: ({ key }) => {
                  if (key === 'logout') {
                    void handleLogout();
                  }
                },
              }}
              placement="bottomRight"
            >
              <span>
                <Avatar size="small" className={styles.headerUserAvatar}>
                  {displayName.slice(0, 1)}
                </Avatar>
                <span className={styles.headerUserText}>
                  <strong>{displayName}</strong>
                  <span>{roleText}</span>
                </span>
              </span>
            </Dropdown>
          </div>
        </Header>
        <Content className={styles.content} ref={contentRef}>
          {/* key 绑定当前路径：路由切换时错误边界随 key 重建，自动从上一页的异常状态中恢复。 */}
          <RouteErrorBoundary key={location.pathname}>
            <Outlet />
          </RouteErrorBoundary>
        </Content>
        <Footer className={styles.footer}>
          <Typography.Text type="secondary">
            MiniAlalipay 运营中心 · 当前为本地骨架，生产数据待接入
          </Typography.Text>
        </Footer>
      </Layout>
    </Layout>
  );
}
