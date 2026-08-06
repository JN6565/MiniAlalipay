import React from 'react';
import { history } from '@umijs/max';
import { Toast, Dialog } from 'antd-mobile';
import './index.less';

const SettingsPage: React.FC = () => {
  const nickname = localStorage.getItem('nickname') || '用户';

  const handleLogout = async () => {
    const result = await Dialog.confirm({
      content: '确定退出登录吗？',
    });

    if (result) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('userId');
      localStorage.removeItem('nickname');
      localStorage.removeItem('userType');
      localStorage.removeItem('accountNumber');
      localStorage.removeItem('session-storage');
      Toast.show({ icon: 'success', content: '已退出登录' });
      window.location.replace('/h5/login');
    }
  };

  return (
    <div className="settings-page">
      {/* 用户信息 */}
      <div className="settings-user">
        <div className="avatar">👤</div>
        <div className="user-info">
          <div className="name">{nickname}</div>
          <div className="id">ID: ****</div>
        </div>
        <span className="arrow">›</span>
      </div>

      {/* 账号与安全 */}
      <div className="settings-section">
        <div className="section-title">账号与安全</div>
        <div className="settings-list">
          <div className="settings-item" onClick={() => history.push('/h5/settings/change-login-password')}>
            <div className="item-icon" style={{ background: '#e6f7ff' }}>🔒</div>
            <div className="item-content">
              <div className="item-title">修改登录密码</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="settings-item" onClick={() => history.push('/h5/payment-password/change')}>
            <div className="item-icon" style={{ background: '#fff7e6' }}>💳</div>
            <div className="item-content">
              <div className="item-title">修改支付密码</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
        </div>
      </div>

      {/* 关于 */}
      <div className="settings-section">
        <div className="section-title">关于</div>
        <div className="settings-list">
          <div className="settings-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#f6ffed' }}>ℹ️</div>
            <div className="item-content">
              <div className="item-title">版本信息</div>
              <div className="item-desc">V1.0.0</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="settings-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#f9f0ff' }}>📋</div>
            <div className="item-content">
              <div className="item-title">用户协议</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="settings-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#fff2f0' }}>🔐</div>
            <div className="item-content">
              <div className="item-title">隐私政策</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
        </div>
      </div>

      {/* 账号操作 */}
      <div className="settings-section">
        <div className="section-title">账号操作</div>
        <div className="settings-list">
          <div className="settings-item" onClick={() => {
            Dialog.confirm({
              content: '确定切换账号吗？',
            }).then((result) => {
              if (result) {
                localStorage.removeItem('accessToken');
                history.push('/h5/login');
              }
            });
          }}>
            <div className="item-icon" style={{ background: '#e6fffb' }}>🔄</div>
            <div className="item-content">
              <div className="item-title">切换账号</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
        </div>
      </div>

      {/* 退出登录 */}
      <div className="settings-footer">
        <button className="logout-btn" onClick={handleLogout}>
          退出登录
        </button>
      </div>
    </div>
  );
};

export default SettingsPage;
