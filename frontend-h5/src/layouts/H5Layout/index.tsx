import React, { useEffect } from 'react';
import { Outlet, useLocation, history } from 'umi';
import { ConfigProvider } from 'antd-mobile';
import zhCN from 'antd-mobile/es/locales/zh-CN';
import './index.less';

const H5Layout: React.FC = () => {
  const location = useLocation();

  // 设置页面标题
  useEffect(() => {
    const title = getPageTitle(location.pathname);
    document.title = title || 'MiniAlalipay';
  }, [location.pathname]);

  // 判断是否显示返回按钮（首页不显示）
  const showBack = location.pathname !== '/h5/home' && location.pathname !== '/h5/login';

  const handleBack = () => {
    history.back();
  };

  return (
    <ConfigProvider locale={zhCN}>
      <div className="h5-layout">
        {/* 导航栏 */}
        {showBack && (
          <div className="navbar">
            <div className="navbar-left" onClick={handleBack}>
              <span className="navbar-back">←</span>
            </div>
            <div className="navbar-title">{getPageTitle(location.pathname)}</div>
            <div className="navbar-right"></div>
          </div>
        )}

        {/* 内容区 */}
        <div className="h5-content">
          <Outlet />
        </div>
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
    '/h5/ai-talk': 'AI助手',
    '/h5/collection': '收款',
    '/h5/credit': 'Mini花呗',
    '/h5/credit/bills': '账单',
    '/h5/credit/repay': '还款',
    '/h5/account/transactions': '交易明细',
    '/h5/account/analytics': '资产分析',
    '/h5/payment-password/setup': '设置支付密码',
    '/h5/payment-password/change': '修改支付密码',
  };

  // 精确匹配
  if (titleMap[pathname]) {
    return titleMap[pathname];
  }

  // 前缀匹配
  for (const [path, title] of Object.entries(titleMap)) {
    if (pathname.startsWith(path)) {
      return title;
    }
  }

  return 'MiniAlalipay';
}

export default H5Layout;
