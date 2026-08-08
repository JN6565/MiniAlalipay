import React, { useEffect } from 'react';
import { Outlet, useLocation, history } from '@umijs/max';
import { ConfigProvider } from 'antd-mobile';
import zhCN from 'antd-mobile/es/locales/zh-CN';
import TabBar, { isTabPath } from '@/components/h5/TabBar';
import TabViews from './TabViews';
import './index.less';

const H5Layout: React.FC = () => {
  const location = useLocation();

  useEffect(() => {
    const publicPaths = ['/h5/login', '/h5/register'];
    const isPublicPage = publicPaths.includes(location.pathname)
      || location.pathname.startsWith('/h5/qr-pay/')
      || location.pathname.startsWith('/h5/collection/pay/');

    // 退出后禁止通过浏览器历史记录重新进入受保护页面并发起无令牌请求。
    if (!isPublicPage && !localStorage.getItem('accessToken')) {
      window.location.replace('/h5/login');
    }
  }, [location.pathname]);

  // 设置页面标题
  useEffect(() => {
    const title = getPageTitle(location.pathname);
    document.title = title || 'MiniAlalipay';
  }, [location.pathname]);

  // 判断是否显示返回按钮（AI助手、联系人不显示）
  const showBack = location.pathname !== '/h5/home' &&
                   location.pathname !== '/h5/login' &&
                   location.pathname !== '/h5/ai-talk' &&
                   location.pathname !== '/h5/contacts' &&
                   location.pathname !== '/h5/profile' &&
                   location.pathname !== '/h5/scan';

  // 判断是否显示导航栏标题（首页、我的、扫码不显示）
  const showTitle = location.pathname !== '/h5/home' &&
                    location.pathname !== '/h5/profile' &&
                    location.pathname !== '/h5/scan';

  const handleBack = () => {
    history.back();
  };

  // Tab 页由保活容器接管渲染，路由组件不再经 Outlet 重复挂载。
  const tabPath = isTabPath(location.pathname);

  return (
    <ConfigProvider locale={zhCN}>
      <div className="h5-layout">
        {/* 导航栏 */}
        {showTitle && (
          <div className="navbar">
            <div className="navbar-left">
              {showBack && (
                <span className="navbar-back" onClick={handleBack}>←</span>
              )}
            </div>
            <div className="navbar-title">{getPageTitle(location.pathname)}</div>
            <div className="navbar-right"></div>
          </div>
        )}

        {/* 内容区：Tab 页走保活容器常驻不卸载，二级页仍由路由 Outlet 渲染 */}
        <div className="h5-content">
          <TabViews />
          {!tabPath && <Outlet />}
        </div>

        {/* 底部导航栏：布局层常驻，切换 Tab 时不重绘 */}
        {tabPath && <TabBar activePath={location.pathname} />}
      </div>
    </ConfigProvider>
  );
};

// 获取页面标题
function getPageTitle(pathname: string): string {
  const titleMap: Record<string, string> = {
    '/h5/login': '登录',
    '/h5/register': '注册',
    '/h5/home': '首页',
    '/h5/settings': '设置',
    '/h5/settings/change-login-password': '修改登录密码',
    '/h5/settings/about': '关于',
    '/h5/transfer': '转账',
    '/h5/transfer/confirm': '确认转账',
    '/h5/recharge': '充值',
    '/h5/ai-talk': 'AI助手',
    '/h5/contacts': '联系人',
    '/h5/friend-requests': '新朋友',
    '/h5/profile': '我的',
    '/h5/scan': '扫一扫',
    '/h5/collection': '收款',
    '/h5/collection/pay': '付款',
    '/h5/collection/result': '支付结果',
    '/h5/credit': 'Mini花呗',
    '/h5/credit/bills': '账单',
    '/h5/credit/repay': '还款',
    '/h5/account/transactions': '交易明细',
    '/h5/account/analytics': '资产分析',
    '/h5/payment-password/setup': '设置支付密码',
    '/h5/payment-password/change': '修改支付密码',
    '/h5/bank-cards': '银行卡',
    '/h5/bank-cards/add': '添加银行卡',
  };

  // 精确匹配
  if (titleMap[pathname]) {
    return titleMap[pathname];
  }

  // 银行卡详情页带路径参数，无法精确命中，统一展示「卡片详情」
  if (pathname.startsWith('/h5/bank-cards/') && pathname !== '/h5/bank-cards/add') {
    return '卡片详情';
  }

  // 前缀匹配（优先匹配更长的路径）
  const sortedPaths = Object.entries(titleMap).sort((a, b) => b[0].length - a[0].length);
  for (const [path, title] of sortedPaths) {
    if (pathname.startsWith(path)) {
      return title;
    }
  }

  return 'MiniAlalipay';
}

export default H5Layout;
