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
      },
      {
        path: '/admin/manual-cases',
        component: '@/pages/ManualCases',
        title: '人工确认台',
      },
      {
        path: '/admin/reports',
        component: '@/pages/Reports',
        title: 'T+1 报表',
      },
      {
        path: '/admin/alerts',
        component: '@/pages/Alerts',
        title: '告警中心',
      },
      {
        path: '/admin/data-quality',
        component: '@/pages/DataQuality',
        title: '数据质量',
      },
      {
        path: '/admin/transactions',
        component: '@/pages/Transactions',
        title: '交易查询与回执',
      },
      {
        path: '/admin/trace',
        component: '@/pages/Trace',
        title: '链路追溯',
      },
      {
        path: '/admin/demo-tasks',
        component: '@/pages/DemoTasks',
        title: '演示任务触发',
      },
      {
        path: '/admin/users',
        component: '@/pages/Users',
        title: '用户管理',
        wrappers: ['@/wrappers/Auth'],
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
