import React, { useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
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

    // TODO: 对接真实接口
    Toast.show({ icon: 'fail', content: '后端服务未启动' });
    setLoading(false);
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
