import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Radio, Toast, SpinLoading } from 'antd-mobile';
import * as qrPayService from '@/services/qrPay';
import * as paymentPasswordService from '@/services/paymentPassword';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { formatCountdown } from '@/utils/format';
import './index.less';

const QrPayPage: React.FC = () => {
  const { token } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [order, setOrder] = useState<qrPayService.QrPayOrder | null>(null);
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'MINI_CREDIT'>('BALANCE');
  const [password, setPassword] = useState('');
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (token) {
      loadOrder(token);
    }
  }, [token]);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const loadOrder = async (t: string) => {
    try {
      // 交换令牌获取订单
      const data = await qrPayService.exchangeToken(t);
      setOrder(data);
      setCountdown(data.remainingSeconds || 300);
    } catch (error: any) {
      Toast.show({ content: error.message || '订单无效', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const handlePay = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    setSubmitting(true);
    try {
      // 1. 生成确认令牌
      const { confirmationToken } = await qrPayService.createConfirmation(
        order!.orderId,
        { paymentPassword: password, fundingSource },
      );

      // 2. 提交支付
      const result = await qrPayService.submitPayment(order!.orderId, {
        confirmationToken,
        fundingSource,
      });

      Toast.show({ icon: 'success', content: '支付成功' });
      history.push(`/h5/qr-pay/receipt/${result.transactionId}`);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '支付失败' });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  if (!order) {
    return <div className="error-state">订单无效或已过期</div>;
  }

  return (
    <div className="qr-pay-page">
      <Card className="order-card">
        <div className="order-header">
          <div className="merchant-name">{order.merchantName}</div>
          <div className="order-countdown">
            剩余时间：{formatCountdown(countdown)}
          </div>
        </div>

        <div className="order-amount">
          <AmountDisplay amountFen={order.amountFen} size="large" />
        </div>

        <div className="order-info">
          <div className="info-row">
            <span className="info-label">商品说明</span>
            <span className="info-value">{order.subject || '-'}</span>
          </div>
        </div>

        <div className="funding-notice">
          仅使用系统虚拟资金，不会扣除真实人民币
        </div>
      </Card>

      <Card className="funding-card">
        <div className="funding-title">选择资金来源</div>
        <Radio.Group value={fundingSource} onChange={(val) => setFundingSource(val as any)}>
          <div className="funding-options">
            <div className="funding-option">
              <Radio value="BALANCE">虚拟余额</Radio>
            </div>
            <div className="funding-option">
              <Radio value="MINI_CREDIT">Mini花呗</Radio>
            </div>
          </div>
        </Radio.Group>
      </Card>

      <Card className="password-card">
        <div className="password-title">请输入支付密码</div>
        <PasswordInput value={password} onChange={setPassword} length={6} />
      </Card>

      <div className="pay-actions">
        <Button
          block
          color="primary"
          size="large"
          loading={submitting}
          onClick={handlePay}
        >
          确认支付
        </Button>
        <Button block size="large" onClick={() => history.back()}>
          取消
        </Button>
      </div>
    </div>
  );
};

export default QrPayPage;
