import React, { useState } from 'react';
import { history, useSearchParams } from '@umijs/max';
import { Toast } from 'antd-mobile';
import { login } from '@/services/auth';
import { ApiError } from '@/services/request';
import { seedNicknamePreference } from '@/utils/profile';
import { IconSet } from '@/components/h5/common';
import './index.less';

const LoginPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validateIdentifier = (value: string) => {
    if (!value) return '请输入手机号或账户号';
    if (!/^1[3-9]\d{9}$/.test(value) && !/^62\d{14}$/.test(value)) {
      return '请输入正确的11位手机号或62开头的16位账户号';
    }
    return '';
  };

  const validatePassword = (value: string) => {
    if (!value) return '请输入登录密码';
    if (value.length < 8 || value.length > 32) return '登录密码长度必须为8-32位';
    return '';
  };

  const updateError = (field: string, message: string) => {
    setErrors((current) => ({ ...current, [field]: message }));
  };

  const handleSubmit = async () => {
    const nextErrors = {
      loginIdentifier: validateIdentifier(loginIdentifier),
      loginPassword: validatePassword(loginPassword),
    };
    setErrors(nextErrors);
    const firstError = Object.values(nextErrors).find(Boolean);
    if (firstError) {
      Toast.show({ content: firstError, icon: 'fail' });
      return;
    }

    setLoading(true);

    try {
      // 调用真实登录接口
      const result = await login({ loginIdentifier, loginPassword });

      // 保存会话令牌到 localStorage
      if (!result?.accessToken || !result?.userId || !result?.accountNumber) {
        throw new Error('登录响应缺少必要的会话信息');
      }

      // 先完整保存会话，再进入首页，保证首页首批请求能够携带认证信息。
      localStorage.setItem('accessToken', result.accessToken);
      localStorage.setItem('userId', result.userId);
      localStorage.setItem('accountNumber', result.accountNumber);
      // 昵称为浏览器本地展示偏好：仅首次登录时用服务端昵称初始化，保留用户本地编辑
      seedNicknamePreference(result.nickname, result.accountNumber);

      // 显示登录成功提示
      Toast.show({ content: '登录成功', icon: 'success' });

      // 跳转到目标页面（如果有 redirect 参数则跳转回去，否则跳转首页）
      const redirect = searchParams.get('redirect');
      if (redirect && redirect.startsWith('/')) {
        window.location.href = redirect;
      } else {
        history.replace('/h5/home');
      }
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        LOGIN_INVALID: '手机号、账户号或密码错误，请核对后重新输入',
        LOGIN_LOCKED: '连续失败次数过多，登录已临时锁定，请15分钟后重试',
        REGISTRATION_PROCESSING: '注册开户处理中，请稍后重试登录',
        NETWORK_ERROR: '网络异常，请检查网络连接后重试',
      };
      Toast.show({ content: messages[code] || error.message || '登录失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* 品牌头图：柔渐变底 + 品牌 Orb 标识 */}
      <div className="login-hero">
        <div className="login-hero-logo">
          <IconSet name="wallet" size={28} color="#fff" />
        </div>
        <div className="login-hero-title">MiniAI 支付</div>
        <div className="login-hero-sub">安全 · 便捷 · 智能</div>
      </div>

      {/* 登录表单卡：悬浮于头图之上 */}
      <div className="login-card">
        <div className="login-field-box">
          <input
            className="login-field-input"
            type="text"
            inputMode="numeric"
            placeholder="手机号或账户号"
            value={loginIdentifier}
            maxLength={16}
            aria-invalid={Boolean(errors.loginIdentifier)}
            onBlur={() => updateError('loginIdentifier', validateIdentifier(loginIdentifier))}
            onChange={(e) => {
              const value = e.target.value.replace(/\D/g, '');
              setLoginIdentifier(value);
              if (errors.loginIdentifier) updateError('loginIdentifier', validateIdentifier(value));
            }}
          />
        </div>
        {errors.loginIdentifier && <div className="login-field-error">{errors.loginIdentifier}</div>}

        <div className="login-field-box">
          <input
            className="login-field-input"
            type={showPassword ? 'text' : 'password'}
            placeholder="登录密码"
            value={loginPassword}
            maxLength={32}
            aria-invalid={Boolean(errors.loginPassword)}
            onBlur={() => updateError('loginPassword', validatePassword(loginPassword))}
            onChange={(e) => {
              const value = e.target.value;
              setLoginPassword(value);
              if (errors.loginPassword) updateError('loginPassword', validatePassword(value));
            }}
          />
          <span className="login-eye" onClick={() => setShowPassword((v) => !v)}>
            <IconSet name={showPassword ? 'eyeOn' : 'eyeOff'} size={16} color="#94a3ba" />
          </span>
        </div>
        {errors.loginPassword && <div className="login-field-error">{errors.loginPassword}</div>}

        <div className="login-forgot" onClick={() => Toast.show({ content: '忘记密码功能暂未开放' })}>
          忘记密码？
        </div>

        <div
          className={`login-submit ${loading ? 'disabled' : ''}`}
          onClick={() => { if (!loading) handleSubmit(); }}
        >
          {loading ? '登录中...' : '登录'}
        </div>

        <div className="login-register-tip">
          还没有账号？<span onClick={() => history.push('/h5/register')}>立即注册</span>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
