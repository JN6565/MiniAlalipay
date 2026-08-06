export default [
  {
    path: '/',
    redirect: '/admin/dashboard',
  },
  {
    path: '/admin/login',
    component: '@/pages/Login',
    title: '运营登录',
  },
  {
    path: '/admin',
    component: '@/layouts/AdminLayout',
    routes: [
      {
        path: '/admin/dashboard',
        component: '@/pages/Dashboard',
        title: '可信运行看板',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/manual-cases',
        component: '@/pages/ManualCases',
        title: '人工确认台',
        wrappers: ['@/wrappers/AdminEntryGuard', '@/wrappers/OperatorGuard'],
      },
      {
        path: '/admin/reports',
        component: '@/pages/Reports',
        title: 'T+1 报表',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/alerts',
        component: '@/pages/Alerts',
        title: '告警中心',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/data-quality',
        component: '@/pages/DataQuality',
        title: '数据质量',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/transactions',
        component: '@/pages/Transactions',
        title: '交易查询与回执',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/trace',
        component: '@/pages/Trace',
        title: '链路追溯',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/alert-rules',
        component: '@/pages/AlertRules',
        title: '告警规则配置',
        wrappers: ['@/wrappers/AdminEntryGuard'],
      },
      {
        path: '/admin/demo-tasks',
        component: '@/pages/DemoTasks',
        title: '演示任务触发',
        wrappers: ['@/wrappers/AdminEntryGuard', '@/wrappers/DemoTasksGuard'],
      },
      {
        path: '/admin/users',
        component: '@/pages/Users',
        title: '用户管理',
        wrappers: ['@/wrappers/AdminEntryGuard', '@/wrappers/UserManagementGuard'],
      },
      {
        path: '/admin/403',
        component: '@/pages/Exception/Forbidden',
        title: '无权访问',
      },
      {
        path: '/admin',
        redirect: '/admin/dashboard',
      },
      {
        path: '*',
        component: '@/pages/Exception/NotFound',
        title: '页面不存在',
      },
    ],
  },
];
