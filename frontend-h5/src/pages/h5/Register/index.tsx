import React, { useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import './index.less';

const RegisterPage: React.FC = () => {
  const [step, setStep] = useState(0);
  const [loginName, setLoginName] = useState('');
  const [nickname, setNickname] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [paymentPassword, setPaymentPassword] = useState('');
  const [confirmPaymentPassword, setConfirmPaymentPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleNext = () => {
    if (step === 0) {
      if (!loginName || loginName.length < 4) {
        Toast.show({ content: '请输入登录名（4-20位）', icon: 'fail' });
        return;
      }
      if (!nickname) {
        Toast.show({ content: '请输入昵称', icon: 'fail' });
        return;
      }
      if (!loginPassword || loginPassword.length < 8) {
        Toast.show({ content: '请输入密码（8-32位）', icon: 'fail' });
        return;
      }
      if (loginPassword !== confirmPassword) {
        Toast.show({ content: '两次密码不一致', icon: 'fail' });
        return;
      }
      setStep(1);
    }
  };

  const handleSubmit = async () => {
    if (!paymentPassword || paymentPassword.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }
    if (paymentPassword !== confirmPaymentPassword) {
      Toast.show({ content: '两次支付密码不一致', icon: 'fail' });
      return;
    }

    setLoading(true);
    // TODO: 对接真实接口
    Toast.show({ icon: 'fail', content: '后端服务未启动' });
    setLoading(false);
  };

  return (
    <div className="register-page">
      {/* 顶部 */}
      <div className="register-top">
        <h1>注册账号</h1>
        <p>创建您的MiniAlalipay账号</p>
      </div>

      {/* 注册卡片 */}
      <div className="register-card">
        {/* 步骤条 */}
        <div className="step-bar">
          <div className={`step ${step === 0 ? 'active' : 'done'}`}>
            <div className="step-num">{step === 0 ? '1' : '✓'}</div>
            <span>基本信息</span>
          </div>
          <div className={`step ${step === 1 ? 'active' : ''}`}>
            <div className="step-num">2</div>
            <span>设置支付密码</span>
          </div>
        </div>

        {step === 0 ? (
          <>
            <div className="field">
              <label>登录名</label>
              <input
                type="text"
                placeholder="请输入登录名（4-20位）"
                value={loginName}
                onChange={(e) => setLoginName(e.target.value)}
              />
            </div>

            <div className="field">
              <label>昵称</label>
              <input
                type="text"
                placeholder="请输入昵称"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
              />
            </div>

            <div className="field">
              <label>密码</label>
              <input
                type="password"
                placeholder="请输入密码（8-32位）"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
              />
            </div>

            <div className="field">
              <label>确认密码</label>
              <input
                type="password"
                placeholder="请再次输入密码"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>

            <button className="register-btn" onClick={handleNext}>
              下一步
            </button>
          </>
        ) : (
          <>
            <div className="field">
              <label>支付密码（6位数字）</label>
              <input
                type="password"
                placeholder="请输入6位数字支付密码"
                maxLength={6}
                value={paymentPassword}
                onChange={(e) => setPaymentPassword(e.target.value.replace(/\D/g, ''))}
              />
            </div>

            <div className="field">
              <label>确认支付密码</label>
              <input
                type="password"
                placeholder="请再次输入6位数字支付密码"
                maxLength={6}
                value={confirmPaymentPassword}
                onChange={(e) => setConfirmPaymentPassword(e.target.value.replace(/\D/g, ''))}
              />
            </div>

            <button
              className="register-btn"
              onClick={handleSubmit}
              disabled={loading}
            >
              {loading ? '注册中...' : '完成注册'}
            </button>
          </>
        )}

        <div className="links">
          <span onClick={() => history.push('/h5/login')}>已有账号？立即登录</span>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
