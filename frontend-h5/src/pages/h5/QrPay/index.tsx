import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Radio, Toast, SpinLoading } from 'antd-mobile';
import * as qrPayService from '@/services/qrPay';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
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
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD' | null>(null);
  const [bankCards, setBankCards] = useState<BankCard[]>([]);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
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
      // 订单交换成功后立即渲染付款页；余额/额度查询失败不能把资金来源控件一起阻塞掉。
      setOrder(exchanged);
      setLoading(false);
      setCountdown(Math.max(0, Math.floor((new Date(exchanged.expiresAt).getTime() - Date.now()) / 1000)));

      const scanned = await qrPayService.markScanned(exchanged.qrOrderId).catch(() => exchanged);
      setOrder(scanned);

      const [accountResult, creditResult, bankCardsResult] = await Promise.allSettled([
        accountService.getMyAccount(),
        creditService.getCreditSummary(),
        getBankCards(),
      ]);
      if (accountResult.status === 'fulfilled') {
        setAccount(accountResult.value as unknown as accountService.AccountInfo);
      }
      if (creditResult.status === 'fulfilled') {
        setCredit(creditResult.value as unknown as creditService.CreditSummary);
      }
      if (bankCardsResult.status === 'fulfilled') {
        setBankCards(bankCardsResult.value as unknown as BankCard[]);
      }
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
        {
          version: order!.version,
          paymentProof,
          fundingSource,
          cardId: fundingSource === 'BANK_CARD' ? selectedCardId || undefined : undefined,
        },
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

  const selectFundingSource = (value: string | number) => {
    setFundingSource(value as 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD');
    setPassword('');
    if (value === 'BANK_CARD' && bankCards.length > 0 && !selectedCardId) {
      setSelectedCardId(bankCards[0].cardId);
    }
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
              <Radio value="BALANCE">
                虚拟余额（可用 <AmountDisplay amountFen={account?.availableFen || 0} size="small" />）
              </Radio>
            </div>
            <div className="funding-option">
              <Radio value="MINI_CREDIT">
                Mini 花呗（可用 <AmountDisplay amountFen={credit?.availableFen || 0} size="small" />）
              </Radio>
            </div>
            <div className="funding-option">
              <Radio value="BANK_CARD">
                银行卡
                {bankCards.length > 0 && selectedCardId && (
                  <span className="card-brief">
                    {bankCards.find(c => c.cardId === selectedCardId)?.bankName}
                    （尾号 {bankCards.find(c => c.cardId === selectedCardId)?.cardLast4}）
                  </span>
                )}
                {bankCards.length > 0 && (
                  <select
                    className="card-select"
                    value={selectedCardId || ''}
                    onClick={e => e.stopPropagation()}
                    onChange={e => setSelectedCardId(e.target.value)}
                  >
                    {bankCards.map(card => (
                      <option key={card.cardId} value={card.cardId}>
                        {card.bankName}（尾号 {card.cardLast4}，余额 ¥{formatBalance(card.balanceFen || 0)}）
                      </option>
                    ))}
                  </select>
                )}
              </Radio>
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
