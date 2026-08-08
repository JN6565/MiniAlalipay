import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Radio, Toast, SpinLoading } from 'antd-mobile';
import * as qrPayService from '@/services/qrPay';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { formatCountdown } from '@/utils/format';
import './index.less';

const QrPayPage: React.FC = () => {
  const { token } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [order, setOrder] = useState<qrPayService.QrPayOrder | null>(null);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'MINI_CREDIT' | null>(null);
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
      if (!localStorage.getItem('accessToken')) {
        history.replace(`/h5/login?redirect=${encodeURIComponent(`/h5/qr-pay/${t}`)}`);
        return;
      }
      // 先建立匿名引导会话，再在同一会话中交换二维码令牌。
      await qrPayService.loadH5Shell(t);
      // 交换令牌获取订单
      const exchanged = await qrPayService.exchangeToken(t);
      const [scanned, accountData, creditData] = await Promise.all([
        qrPayService.markScanned(exchanged.qrOrderId),
        accountService.getMyAccount(),
        creditService.getCreditSummary(),
      ]);
      setOrder(scanned as qrPayService.QrPayOrder);
      setAccount(accountData as unknown as accountService.AccountInfo);
      setCredit(creditData as unknown as creditService.CreditSummary);
      setCountdown(Math.max(0, Math.floor((new Date(scanned.expiresAt).getTime() - Date.now()) / 1000)));
    } catch (error: any) {
      Toast.show({ content: error.message || '订单无效', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const handlePay = async () => {
    if (!fundingSource) {
      Toast.show({ content: '请选择支付方式', icon: 'fail' });
      return;
    }
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入 6 位支付密码', icon: 'fail' });
      return;
    }

    setSubmitting(true);
    try {
      // 支付密码证明与确认令牌均为一次性敏感数据，只保存在当前调用栈中。
      const { paymentProof } = await paymentPasswordService.issuePaymentProof(
        password,
        'QR_PAY_CONFIRM',
      );
      const { confirmationToken } = await qrPayService.createConfirmation(
        order!.qrOrderId,
        { version: order!.version, paymentProof, fundingSource },
      );

      const result = await qrPayService.submitPayment(order!.qrOrderId, {
        confirmationToken,
      });

      Toast.show({ icon: 'success', content: '支付已受理' });
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

  const balanceDisabled = account?.status !== 'ACTIVE'
    || (account?.availableFen ?? 0) < order.amountFen;
  const creditDisabled = credit?.status !== 'ACTIVE'
    || (credit?.availableFen ?? 0) < order.amountFen
    || (credit?.overdueFen ?? 0) > 0;

  const selectFundingSource = (value: string | number) => {
    setFundingSource(value as 'BALANCE' | 'MINI_CREDIT');
    setPassword('');
  };

  return (
    <div className="qr-pay-page">
      <Card className="order-card">
        <div className="order-header">
          <div className="merchant-name">{order.payeeDisplayName || '收款用户'}</div>
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
        <Radio.Group value={fundingSource || undefined} onChange={selectFundingSource}>
          <div className="funding-options">
            <div className="funding-option">
              <Radio value="BALANCE" disabled={balanceDisabled}>
                虚拟余额（可用 <AmountDisplay amountFen={account?.availableFen || 0} size="small" />）
              </Radio>
              {balanceDisabled ? <div className="funding-reason">可用余额不足或账户不可用</div> : null}
            </div>
            <div className="funding-option">
              <Radio value="MINI_CREDIT" disabled={creditDisabled}>
                Mini 花呗（可用 <AmountDisplay amountFen={credit?.availableFen || 0} size="small" />）
              </Radio>
              {creditDisabled ? <div className="funding-reason">花呗不可用、存在逾期或额度不足</div> : null}
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
          disabled={!fundingSource}
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
