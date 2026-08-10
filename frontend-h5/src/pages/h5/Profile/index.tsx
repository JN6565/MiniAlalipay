import React, { useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { Toast, Dialog } from 'antd-mobile';
import { clearSession } from '@/services/request';
import * as userService from '@/services/user';
import { getIdentity } from '@/services/identity';
import { formatAccountName, getAvatarDisplay, getProfilePreference } from '@/utils/profile';
import './index.less';

const ProfilePage: React.FC = () => {
  const profilePreference = getProfilePreference();
  const nickname = profilePreference.nickname;
  const [accountNumber, setAccountNumber] = useState(localStorage.getItem('accountNumber') || '');
  const [maskedPhone, setMaskedPhone] = useState<string>('');
  /** 是否已绑定身份（identityStatus 为 VERIFIED），接口失败时保持未绑定展示。 */
  const [identityBound, setIdentityBound] = useState(false);

  useEffect(() => {
    userService.getMyInfo().then(info => {
      if (info.accountNumber) setAccountNumber(info.accountNumber);
      setMaskedPhone(info.maskedPhone || '');
    }).catch(() => {});
    getIdentity().then(info => {
      setIdentityBound(info.identityStatus === 'VERIFIED');
    }).catch(() => {});
  }, []);

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
        <div className="avatar">
          {profilePreference.avatarDataUrl ? (
            <img src={profilePreference.avatarDataUrl} alt="头像" />
          ) : (
            getAvatarDisplay(profilePreference.avatarCode)
          )}
        </div>
        <div className="user-info">
          <div className="name">{nickname}</div>
          {identityBound && <div className="identity-status">已绑定身份</div>}
          <div className="account-name">{formatAccountName(accountNumber)}</div>
          <div className="id">{maskedPhone || '加载中...'}</div>
        </div>
        <button
          type="button"
          className="profile-settings-button"
          aria-label="编辑个人资料"
          onClick={() => history.push('/h5/profile/edit')}
        >
          <span aria-hidden="true">›</span>
        </button>
      </div>

      {/* 账号与安全 */}
      <div className="profile-section">
        <div className="section-title">账号与安全</div>
        <div className="profile-list">
          <div className="profile-item" onClick={() => history.push('/h5/identity-bind')}>
            <div className="item-icon" style={{ background: '#f0f5ff' }}>🪪</div>
            <div className="item-content">
              <div className="item-title">身份绑定</div>
              <div className="item-desc">{identityBound ? '已绑定' : '未绑定'}</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
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
          <div className="profile-item" onClick={() => history.push('/h5/settings/version')}>
            <div className="item-icon" style={{ background: '#f6ffed' }}>ℹ️</div>
            <div className="item-content">
              <div className="item-title">版本信息</div>
              <div className="item-desc">V1.0.0</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="profile-item" onClick={() => history.push('/h5/settings/user-agreement')}>
            <div className="item-icon" style={{ background: '#f9f0ff' }}>📋</div>
            <div className="item-content">
              <div className="item-title">用户协议</div>
            </div>
            <span className="item-arrow">›</span>
          </div>
          <div className="profile-item" onClick={() => history.push('/h5/settings/privacy-policy')}>
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
    </div>
  );
};

export default ProfilePage;
