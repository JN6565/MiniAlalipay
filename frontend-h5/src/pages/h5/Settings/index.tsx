import React from 'react';
import { history } from '@umijs/max';
import { Toast, Dialog } from 'antd-mobile';
import { clearSession } from '@/services/request';
import { IconSet, IconName } from '@/components/h5/common';
import './index.less';

/** 设置项配置（V2 定稿）：关于类 + 安全类，统一图标底 + 右箭头。 */
const SETTING_ITEMS: { label: string; icon: IconName; path: string; desc?: string }[] = [
  { label: '版本信息', icon: 'shield', path: '/h5/settings/version', desc: 'V1.0.0' },
  { label: '用户协议', icon: 'receipt', path: '/h5/settings/user-agreement' },
  { label: '隐私政策', icon: 'lock', path: '/h5/settings/privacy-policy' },
  { label: '修改登录密码', icon: 'setting', path: '/h5/settings/change-login-password' },
  { label: '修改支付密码', icon: 'card', path: '/h5/payment-password/change' },
];

/**
 * 设置页（V2 重设计）：版本信息/用户协议/隐私政策/改密入口列表，
 * 底部「切换账号」（蓝描边）与「退出登录」（红描边）双按钮。
 */
const SettingsPage: React.FC = () => {
  const handleLogout = async () => {
    const result = await Dialog.confirm({
      content: '确定退出登录吗？',
    });

    if (result) {
      clearSession();
      Toast.show({ icon: 'success', content: '已退出登录' });
      window.location.replace('/h5/login');
    }
  };

  /** 切换账号 = 清除本地会话并跳转登录页。 */
  const handleSwitchAccount = async () => {
    const result = await Dialog.confirm({
      content: '确定切换账号吗？',
    });

    if (result) {
      clearSession();
      window.location.replace('/h5/login');
    }
  };

  return (
    <div className="settings-page">
      <div className="settings-list">
        {SETTING_ITEMS.map((item, index) => (
          <div
            key={item.label}
            className={`settings-item${index < SETTING_ITEMS.length - 1 ? '' : ' last'}`}
            onClick={() => history.push(item.path)}
          >
            <div className="item-icon">
              <IconSet name={item.icon} size={16} color="var(--h5-primary)" />
            </div>
            <div className="item-content">
              <div className="item-title">{item.label}</div>
            </div>
            {item.desc && <span className="item-desc">{item.desc}</span>}
            <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
          </div>
        ))}
      </div>

      {/* 账号操作：蓝描边切换账号 + 红描边退出登录 */}
      <div className="settings-actions">
        <div className="action-btn switch" onClick={handleSwitchAccount}>切换账号</div>
        <div className="action-btn logout" onClick={handleLogout}>退出登录</div>
      </div>
    </div>
  );
};

export default SettingsPage;
