import React, { useState } from 'react';
import { history } from 'umi';
import { Form, Input, Button, Toast } from 'antd-mobile';
import * as paymentPasswordService from '@/services/paymentPassword';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

const PaymentPasswordChangePage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [loginPassword, setLoginPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSubmit = async () => {
    if (!loginPassword) {
      Toast.show({ content: '请输入登录密码', icon: 'fail' });
      return;
    }

    if (!newPassword || newPassword.length !== 6) {
      Toast.show({ content: '请输入6位新支付密码', icon: 'fail' });
      return;
    }

    if (newPassword !== confirmPassword) {
      Toast.show({ content: '两次密码不一致', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      await paymentPasswordService.changePaymentPassword({
        loginPassword,
        newPaymentPassword: newPassword,
      });
      Toast.show({ icon: 'success', content: '支付密码修改成功' });
      history.push('/h5/settings');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '修改失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="payment-password-change-page">
      <div className="change-form">
        <div className="form-item">
          <div className="form-label">登录密码</div>
          <Input
            type="password"
            placeholder="请输入当前登录密码"
            value={loginPassword}
            onChange={setLoginPassword}
          />
        </div>

        <div className="form-item">
          <div className="form-label">新支付密码</div>
          <PasswordInput value={newPassword} onChange={setNewPassword} length={6} />
        </div>

        <div className="form-item">
          <div className="form-label">确认新支付密码</div>
          <PasswordInput value={confirmPassword} onChange={setConfirmPassword} length={6} />
        </div>

        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleSubmit}
        >
          确认修改
        </Button>
      </div>
    </div>
  );
};

export default PaymentPasswordChangePage;
