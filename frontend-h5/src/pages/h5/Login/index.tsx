import React, { useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { login } from '@/services/auth';
import './index.less';

const LoginPage: React.FC = () => {
  const [loginName, setLoginName] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!loginName || loginName.length < 4) {
      Toast.show({ content: '请输入登录名（4-20位）', icon: 'fail' });
      return;
    }

    if (!loginPassword || loginPassword.length < 8) {
      Toast.show({ content: '请输入密码（8-32位）', icon: 'fail' });
      return;
    }

    setLoading(true);

    try {
      // 调用真实登录接口
      const result = await login({ loginName, loginPassword });

      // 保存会话令牌到 localStorage
      localStorage.setItem('accessToken', result.accessToken);
      localStorage.setItem('userId', result.userId);
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

        <div className="field">
          <input
            type="text"
            placeholder="请输入登录名"
            value={loginName}
            onChange={(e) => setLoginName(e.target.value)}
          />
        </div>

        <div className="field">
          <input
            type="password"
            placeholder="请输入密码"
            value={loginPassword}
            onChange={(e) => setLoginPassword(e.target.value)}
          />
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
