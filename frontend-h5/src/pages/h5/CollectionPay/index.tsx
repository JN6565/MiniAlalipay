import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Input, Toast, SpinLoading } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import { AmountInput } from '@/components/h5/AmountInput';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import './index.less';

const CollectionPayPage: React.FC = () => {
  const { token } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [order, setOrder] = useState<collectionService.CollectionOrder | null>(null);
  const [amount, setAmount] = useState<number>(0);
  const [subject, setSubject] = useState('扫码付款');
  const [password, setPassword] = useState('');

  useEffect(() => {
    if (token) {
      loadOrder(token);
    }
  }, [token]);

  const loadOrder = async (t: string) => {
    try {
      // 1. 先调用 bootstrap 接口获取会话（不需要登录）
      await collectionService.bootstrapToken(t);

      // 2. 检查是否已登录
      const accessToken = localStorage.getItem('accessToken');
      if (!accessToken) {
        // 未登录，跳转登录页，登录后回来
        window.location.href = `/h5/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
        return;
      }

      // 3. 交换令牌获取订单
      const data = await collectionService.exchangeToken(t);
      setOrder(data);
      if (data.amountFen) {
        setAmount(data.amountFen / 100);
      }
      if (data.subject) {
        setSubject(data.subject);
      }
    } catch (error: any) {
      // 如果是认证失败，跳转登录
      if (error.code === 'COMMON_UNAUTHORIZED' || error.code === 'AUTH_REQUIRED') {
        window.location.href = `/h5/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
        return;
      }
      Toast.show({ content: error.message || '订单无效', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const handleLockAmount = async () => {
    if (!order) return;
    if (amount < 0.01 || amount > 50000) {
      Toast.show({ content: '金额范围0.01-50000元', icon: 'fail' });
      return;
    }

    try {
      const updated = await collectionService.lockOrderAmount(order.orderId, {
        version: order.version || 0,
        amountFen: Math.round(amount * 100),
        subject,
      });
      setOrder(updated);
      Toast.show({ icon: 'success', content: '金额已锁定' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '锁定失败' });
    }
  };

  const handlePay = async () => {
    if (!order) return;
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    setSubmitting(true);
    try {
      // 1. 用支付密码向 user-center 申请证明令牌
      const proofToken = await collectionService.issuePaymentProof(password);

      // 2. 用证明令牌生成确认令牌
      const { confirmationToken } = await collectionService.createOrderConfirmation(
        order.orderId,
        proofToken,
        order.version || 0,
      );

      // 3. 提交支付
      const result = await collectionService.submitPayment(
        order.orderId,
        confirmationToken,
      );

      Toast.show({ icon: 'success', content: '支付成功' });
      history.push(`/h5/collection/result/${result.collectionOrderId}`);
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
    <div className="collection-pay-page">
      <Card className="payee-card">
        <div className="payee-title">收款人</div>
        <div className="payee-name">{order.payeeName}</div>
      </Card>

      {order.editable ? (
        <Card className="amount-card">
          <div className="amount-title">填写金额</div>
          <AmountInput
            value={amount}
            onChange={setAmount}
            placeholder="请输入付款金额"
          />
          <Input
            placeholder="备注（选填）"
            value={subject}
            onChange={setSubject}
            maxLength={50}
          />
          <Button
            block
            size="small"
            onClick={handleLockAmount}
            style={{ marginTop: 12 }}
          >
            确定金额
          </Button>
        </Card>
      ) : (
        <Card className="amount-card">
          <div className="amount-title">付款金额</div>
          <div className="amount-value">
            <AmountDisplay amountFen={order.amountFen || 0} size="large" />
          </div>
          {order.subject && (
            <div className="amount-subject">备注：{order.subject}</div>
          )}
        </Card>
      )}

      {/* 金额已锁定后显示确认信息和密码输入 */}
      {order.status !== 'DRAFT' && (
        <>
          <Card className="info-card">
            <div className="info-row">
              <span className="info-label">手续费</span>
              <span className="info-value">¥0.00</span>
            </div>
            <div className="info-row">
              <span className="info-label">实际扣款</span>
              <span className="info-value">
                <AmountDisplay amountFen={order.amountFen || Math.round(amount * 100)} />
              </span>
            </div>
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
        </>
      )}
    </div>
  );
};

export default CollectionPayPage;
