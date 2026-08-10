import React, { useState } from 'react';
import { history, useSearchParams } from '@umijs/max';
import { Toast } from 'antd-mobile';
import { login } from '@/services/auth';
import { ApiError } from '@/services/request';
import { seedNicknamePreference } from '@/utils/profile';
import './index.less';

const LoginPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
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
        LOGIN_INVALID: '手机号、账户号或密码错误',
        LOGIN_LOCKED: '登录已被临时锁定，请稍后再试',
        REGISTRATION_PROCESSING: '注册开户处理中，请稍后再试',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ content: messages[code] || error.message || '登录失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* 顶部蓝色区域 */}
      <div className="login-top">
        <div className="logo">💰</div>
        <h1>MiniAlalipay</h1>
        <p>AI加持的确定性金融信任平台</p>
      </div>


      {/* 登录卡片 */}
      <div className="login-card">
        <h2>登录</h2>

        <div className={`field ${errors.loginIdentifier ? 'has-error' : ''}`}>
          <input
            type="text"
            inputMode="numeric"
            placeholder="请输入手机号或账户号"
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
          {errors.loginIdentifier && <div className="field-error">{errors.loginIdentifier}</div>}
        </div>

        <div className={`field ${errors.loginPassword ? 'has-error' : ''}`}>
          <input
            type="password"
            placeholder="请输入密码"
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
          {errors.loginPassword && <div className="field-error">{errors.loginPassword}</div>}
        </div>

        <button
          className="login-btn"
          onClick={handleSubmit}
          disabled={loading}
        >
          {loading ? '登录中...' : '登录'}
        </button>

        <div className="links">
          <span onClick={() => history.push('/h5/register')}>注册账号</span>
          <span>|</span>
          <span onClick={() => Toast.show({ content: '忘记密码功能暂未开放' })}>忘记密码</span>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
