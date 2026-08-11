import React, { useState } from 'react';
import { history } from '@umijs/max';
import { Toast } from 'antd-mobile';
import * as paymentPasswordService from '@/services/paymentPassword';
import { ApiError } from '@/services/request';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

const PaymentPasswordChangePage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSubmit = async () => {
    if (!currentPassword) {
      Toast.show({ content: '请输入当前支付密码', icon: 'fail' });
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
        currentPassword,
        newPassword,
      });
      Toast.show({ icon: 'success', content: '支付密码修改成功' });
      history.push('/h5/settings');
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        PAY_PASSWORD_INVALID: '当前支付密码错误',
        PAYMENT_LOCKED: '支付密码已被临时锁定，请稍后再试',
        PASSWORD_POLICY_VIOLATION: '支付密码必须为6位数字',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ icon: 'fail', content: messages[code] || error.message || '修改失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="payment-password-change-page">
      <div className="ppc-card">
        <div className="ppc-field">
          <div className="ppc-field-label">当前支付密码</div>
          <PasswordInput value={currentPassword} onChange={setCurrentPassword} length={6} />
        </div>

        <div className="ppc-field">
          <div className="ppc-field-label">新支付密码</div>
          <PasswordInput value={newPassword} onChange={setNewPassword} length={6} />
        </div>

        <div className="ppc-field">
          <div className="ppc-field-label">确认新支付密码</div>
          <PasswordInput value={confirmPassword} onChange={setConfirmPassword} length={6} />
        </div>

        <div className="ppc-tip">支付密码为 6 位数字，用于转账、充值、提现等资金操作确认</div>
      </div>

      <div
        className={`ppc-submit ${loading ? 'disabled' : ''}`}
        onClick={() => { if (!loading) handleSubmit(); }}
      >
        {loading ? '提交中...' : '确认修改'}
      </div>
    </div>
  );
};

export default PaymentPasswordChangePage;
