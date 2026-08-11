import React, { useState } from 'react';
import { Toast } from 'antd-mobile';
import * as authService from '@/services/auth';
import { ApiError, clearSession } from '@/services/request';
import { history } from '@umijs/max';
import { IconSet } from '@/components/h5/common';
import './index.less';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/;

/** 密码强度：按「长度/小写/大写/数字/符号」五项计分，映射弱/中/强。 */
function strengthOf(pwd: string): { level: 0 | 1 | 2 | 3; label: string } {
  if (!pwd) return { level: 0, label: '' };
  let score = 0;
  if (pwd.length >= 8) score += 1;
  if (/[a-z]/.test(pwd)) score += 1;
  if (/[A-Z]/.test(pwd)) score += 1;
  if (/\d/.test(pwd)) score += 1;
  if (/[^a-zA-Z0-9]/.test(pwd)) score += 1;
  if (score <= 2) return { level: 1, label: '弱' };
  if (score <= 4) return { level: 2, label: '中' };
  return { level: 3, label: '强' };
}

const ChangeLoginPasswordPage: React.FC = () => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [visible, setVisible] = useState({ current: false, next: false, confirm: false });
  const [loading, setLoading] = useState(false);

  const strength = strengthOf(newPassword);

  const handleSubmit = async () => {
    if (!currentPassword) {
      Toast.show({ content: '请输入当前密码', icon: 'fail' });
      return;
    }
    if (!PASSWORD_PATTERN.test(newPassword)) {
      Toast.show({ content: '新密码必须为8-32位，并包含大小写字母和数字', icon: 'fail' });
      return;
    }
    if (newPassword !== confirmPassword) {
      Toast.show({ content: '两次密码不一致', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      await authService.changeLoginPassword({ currentPassword, newPassword });
      // 密码修改成功后旧会话必须立即废弃，避免重新登录前继续携带旧令牌。
      clearSession();
      Toast.show({ icon: 'success', content: '密码修改成功，请重新登录' });
      history.replace('/h5/login');
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        CURRENT_LOGIN_PASSWORD_INVALID: '当前登录密码错误',
        PASSWORD_REUSE_NOT_ALLOWED: '新密码不能与当前密码相同',
        PASSWORD_POLICY_VIOLATION: '新密码必须为8-32位，并包含大小写字母和数字',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ icon: 'fail', content: messages[code] || error.message || '修改失败' });
    } finally {
      setLoading(false);
    }
  };

  const renderField = (key: 'current' | 'next' | 'confirm', label: string, placeholder: string,
    value: string, onChange: (v: string) => void) => (
    <div className="clp-field">
      <div className="clp-field-label">{label}</div>
      <div className="clp-field-box">
        <input
          className="clp-field-input"
          type={visible[key] ? 'text' : 'password'}
          placeholder={placeholder}
          maxLength={32}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <span className="clp-eye" onClick={() => setVisible((v) => ({ ...v, [key]: !v[key] }))}>
          <IconSet name={visible[key] ? 'eyeOn' : 'eyeOff'} size={16} color="#94a3ba" />
        </span>
      </div>
    </div>
  );

  return (
    <div className="change-password-page">
      <div className="clp-card">
        {renderField('current', '原密码', '请输入当前登录密码', currentPassword, setCurrentPassword)}
        {renderField('next', '新密码', '8-32位，包含大小写字母和数字', newPassword, setNewPassword)}

        {/* 密码强度指示 */}
        <div className="clp-strength">
          <div className="clp-strength-bars">
            {[1, 2, 3, 4].map((i) => {
              const active = strength.level > 0 && i <= (strength.level === 1 ? 1 : strength.level === 2 ? 3 : 4);
              const color = strength.level === 1 ? '#f0484e' : strength.level === 2 ? '#f59f2d' : '#16b387';
              return (
                <div
                  key={i}
                  className="clp-strength-bar"
                  style={{ background: active ? color : '#e2e8f2' }}
                />
              );
            })}
          </div>
          {newPassword && (
            <div className="clp-strength-tip">
              密码强度：{strength.label} · 建议包含字母与数字，长度 ≥ 8 位
            </div>
          )}
        </div>

        {renderField('confirm', '确认新密码', '请再次输入新密码', confirmPassword, setConfirmPassword)}
      </div>

      <div
        className={`clp-submit ${loading ? 'disabled' : ''}`}
        onClick={() => { if (!loading) handleSubmit(); }}
      >
        {loading ? '提交中...' : '确认修改'}
      </div>
    </div>
  );
};

export default ChangeLoginPasswordPage;
