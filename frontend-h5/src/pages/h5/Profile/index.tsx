import React from 'react';
import { history } from '@umijs/max';
import { Toast, Dialog } from 'antd-mobile';
import { clearSession } from '@/services/request';
import './index.less';

const ProfilePage: React.FC = () => {
  const nickname = localStorage.getItem('nickname') || '用户';

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

  return (
    <div className="profile-page">
      {/* 用户信息 */}
      <div className="profile-user">
        <div className="avatar">👤</div>
        <div className="user-info">
          <div className="name">{nickname}</div>
          <div className="id">ID: ****</div>
        </div>
        <span className="arrow">›</span>
      </div>

      {/* 账号与安全 */}
      <div className="profile-section">
        <div className="section-title">账号与安全</div>
        <div className="profile-list">
          <div className="profile-item" onClick={() => history.push('/h5/settings/change-login-password')}>
            <div className="item-icon" style={{ background: '#e6f7ff' }}>🔒</div>
            <div className="item-content">
              <div className="item-title">修改登录密码</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="profile-item" onClick={() => history.push('/h5/payment-password/change')}>
            <div className="item-icon" style={{ background: '#fff7e6' }}>💳</div>
            <div className="item-content">
              <div className="item-title">修改支付密码</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
        </div>
      </div>

      {/* 关于 */}
      <div className="profile-section">
        <div className="section-title">关于</div>
        <div className="profile-list">
          <div className="profile-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#f6ffed' }}>ℹ️</div>
            <div className="item-content">
              <div className="item-title">版本信息</div>
              <div className="item-desc">V1.0.0</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="profile-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#f9f0ff' }}>📋</div>
            <div className="item-content">
              <div className="item-title">用户协议</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="profile-item" onClick={() => history.push('/h5/settings/about')}>
            <div className="item-icon" style={{ background: '#fff2f0' }}>🔐</div>
            <div className="item-content">
              <div className="item-title">隐私政策</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
        </div>
      </div>

      {/* 账号操作 */}
      <div className="profile-section">
        <div className="section-title">账号操作</div>
        <div className="profile-list">
          <div className="profile-item" onClick={() => {
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
      <div className="profile-footer">
        <button className="logout-btn" onClick={handleLogout}>
          退出登录
        </button>
      </div>

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab" onClick={() => history.push('/h5/home')}>
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/ai-talk')}>
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/contacts')}>
          <span className="tab-icon">👥</span>
          <span className="tab-label">联系人</span>
        </div>
        <div className="tab on">
          <span className="tab-icon">👤</span>
          <span className="tab-label">我的</span>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
