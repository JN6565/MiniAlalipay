import React, { useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { login } from '@/services/auth';
import './index.less';

const LoginPage: React.FC = () => {
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
      localStorage.setItem('accessToken', result.accessToken);
      localStorage.setItem('userId', result.userId);
      localStorage.setItem('accountNumber', result.accountNumber);
      localStorage.setItem('nickname', result.nickname);

      // 显示登录成功提示
      Toast.show({ content: '登录成功', icon: 'success' });

      // 跳转到首页
      history.push('/h5/home');
    } catch (error: any) {
      // 错误已在 request 拦截器中处理，这里可以添加额外处理
      console.error('登录失败:', error);
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
          <span>忘记密码</span>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
