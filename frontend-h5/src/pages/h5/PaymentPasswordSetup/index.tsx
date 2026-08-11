import React, { useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as paymentPasswordService from '@/services/paymentPassword';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { IconSet } from '@/components/h5/common';
import './index.less';

const PaymentPasswordSetupPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSubmit = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    if (password !== confirmPassword) {
      Toast.show({ content: '两次密码不一致', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      await paymentPasswordService.setupPaymentPassword(password);
      Toast.show({ icon: 'success', content: '支付密码设置成功' });
      history.push('/h5/home');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '设置失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="payment-password-setup-page">
      {/* 顶部引导：盾牌图标 + 说明 */}
      <div className="pps-hero">
        <div className="pps-hero-icon">
          <IconSet name="shield" size={22} color="#256cff" />
        </div>
        <div className="pps-hero-title">设置支付密码</div>
        <div className="pps-hero-desc">支付密码用于资金交易确认，请设置 6 位数字密码</div>
      </div>

      <div className="pps-card">
        <div className="pps-field">
          <div className="pps-field-label">支付密码</div>
          <PasswordInput value={password} onChange={setPassword} length={6} />
        </div>

        <div className="pps-field">
          <div className="pps-field-label">确认支付密码</div>
          <PasswordInput value={confirmPassword} onChange={setConfirmPassword} length={6} />
        </div>
      </div>

      <div
        className={`pps-submit ${loading ? 'disabled' : ''}`}
        onClick={() => { if (!loading) handleSubmit(); }}
      >
        {loading ? '提交中...' : '确认设置'}
      </div>
    </div>
  );
};

export default PaymentPasswordSetupPage;
