import React, { useState } from 'react';
import { history } from 'umi';
import { Form, Button, Toast } from 'antd-mobile';
import * as paymentPasswordService from '@/services/paymentPassword';
import { PasswordInput } from '@/components/h5/PasswordInput';
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
      <div className="setup-header">
        <h1 className="setup-title">设置支付密码</h1>
        <p className="setup-desc">支付密码用于资金交易确认，请设置6位数字密码</p>
      </div>

      <div className="setup-form">
        <div className="form-item">
          <div className="form-label">支付密码</div>
          <PasswordInput value={password} onChange={setPassword} length={6} />
        </div>

        <div className="form-item">
          <div className="form-label">确认支付密码</div>
          <PasswordInput value={confirmPassword} onChange={setConfirmPassword} length={6} />
        </div>

        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleSubmit}
        >
          确认设置
        </Button>
      </div>
    </div>
  );
};

export default PaymentPasswordSetupPage;
