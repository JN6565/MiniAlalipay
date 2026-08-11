import React, { useEffect, useState } from 'react';
import { useParams, history, useLocation } from 'umi';
import { Toast, SpinLoading, Popup } from 'antd-mobile';
import * as qrPayService from '@/services/qrPay';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { IconSet } from '@/components/h5/common';
import { formatCountdown } from '@/utils/format';
import './index.less';

const QrPayPage: React.FC = () => {
  const { token } = useParams();
  const location = useLocation();
  // 短码兑换路径：兑换端点已将动态订单绑定当前会话，路由参数是订单 ID，
  // 无需再走 H5 壳加载/令牌交换，直接按 ID 查单。
  const viaShortCode = new URLSearchParams(location.search).get('via') === 'short-code';
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
  const [showCardPicker, setShowCardPicker] = useState(false);

  useEffect(() => {
    if (token) {
      loadOrder(token);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
        const current = `/h5/qr-pay/${t}${viaShortCode ? '?via=short-code' : ''}`;
        history.replace(`/h5/login?redirect=${encodeURIComponent(current)}`);
        return;
      }
      let exchanged: qrPayService.QrPayOrder;
      if (viaShortCode) {
        // 短码兑换已在服务端完成会话绑定，此处 t 为订单 ID
        exchanged = await qrPayService.getOrderStatus(t) as unknown as qrPayService.QrPayOrder;
      } else {
        // 先建立匿名引导会话，再在同一会话中交换二维码令牌。
        await qrPayService.loadH5Shell(t);
        // 交换令牌获取订单
        exchanged = await qrPayService.exchangeToken(t);
      }
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
      // 回执页无独立查询接口，展示信息通过路由 state 携带（不含敏感数据）
      history.push(`/h5/qr-pay/receipt/${result.transactionId}`, {
        payeeName: order!.payeeDisplayName || '收款用户',
        amountFen: order!.amountFen,
        fundingSource,
      });
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

  const selectedCard = bankCards.find((c) => c.cardId === selectedCardId) || null;

  const selectFundingSource = (value: 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD') => {
    setFundingSource(value);
    setPassword('');
    if (value === 'BANK_CARD') {
      if (bankCards.length === 0) {
        Toast.show({ content: '暂无银行卡，请先绑定', icon: 'fail' });
        return;
      }
      if (!selectedCardId) setSelectedCardId(bankCards[0].cardId);
      setShowCardPicker(true);
    }
  };

  const fundingRows: Array<{
    key: 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD';
    label: string;
    value: string;
  }> = [
    { key: 'BALANCE', label: '账户余额', value: `可用 ¥${formatBalance(account?.availableFen || 0)}` },
    { key: 'MINI_CREDIT', label: 'Mini 花呗', value: `可用额度 ¥${formatBalance(credit?.availableFen || 0)}` },
    {
      key: 'BANK_CARD',
      label: '银行卡',
      value: selectedCard ? `${selectedCard.bankName}（${selectedCard.cardLast4}）` : '选择银行卡',
    },
  ];

  return (
    <div className="qr-pay-page">
      <div className="qp-hero" />

      <div className="qp-body">
        {/* 商户/订单信息卡 */}
        <div className="qp-card order-card">
          <div className="order-tile">
            <IconSet name="qr" size={18} color="#fff" />
          </div>
          <div className="merchant-name">{order.payeeDisplayName || '收款用户'}</div>
          <div className="order-countdown">剩余时间：{formatCountdown(countdown)}</div>
          {order.subject && <div className="order-subject">商品说明：{order.subject}</div>}
        </div>

        {/* 金额卡 */}
        <div className="qp-card amount-card">
          <div className="amount-label">支付金额</div>
          <div className="amount-value">¥{formatBalance(order.amountFen)}</div>
          <div className="amount-tips">仅使用系统虚拟资金，不会扣除真实人民币</div>
        </div>

        {/* 资金来源行（自定义单选） */}
        <div className="qp-card funding-rows">
          {fundingRows.map((row) => {
            const on = fundingSource === row.key;
            return (
              <div className="funding-row" key={row.key} onClick={() => selectFundingSource(row.key)}>
                <span className="funding-label">{row.label}</span>
                <span className="funding-value">{row.value}</span>
                <span className={`funding-radio${on ? ' active' : ''}`}>
                  {on && <span className="funding-radio-dot" />}
                </span>
              </div>
            );
          })}
        </div>

        {/* 密码卡 */}
        <div className="qp-card password-card">
          <div className="password-title">请输入支付密码</div>
          <PasswordInput value={password} onChange={setPassword} length={6} />
        </div>

        <div
          className={`h5-btn-gradient qp-submit${submitting || !fundingSource ? ' disabled' : ''}`}
          onClick={() => !submitting && handlePay()}
        >
          {submitting ? '提交中...' : '确认支付'}
        </div>
        <div className="qp-cancel" onClick={() => history.back()}>
          取消
        </div>
      </div>

      {/* 银行卡选择 Popup */}
      <Popup
        visible={showCardPicker}
        onMaskClick={() => setShowCardPicker(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '14px 14px 20px' }}
      >
        <div className="card-picker">
          <div className="picker-handle" />
          <div className="picker-title">选择付款银行卡</div>
          {bankCards.map((card) => {
            const on = card.cardId === selectedCardId;
            return (
              <div
                key={card.cardId}
                className={`picker-row${on ? ' active' : ''}`}
                onClick={() => {
                  setSelectedCardId(card.cardId);
                  setShowCardPicker(false);
                }}
              >
                <span className={`picker-logo${on ? ' active' : ''}`}>
                  {on ? <IconSet name="check" size={14} color="#fff" /> : card.bankName.slice(0, 1)}
                </span>
                <span className="picker-name">
                  {card.bankName} · 余额 ¥{formatBalance(card.balanceFen || 0)}（{card.cardLast4}）
                </span>
                {on ? (
                  <IconSet name="check" size={16} color="var(--h5-primary)" />
                ) : (
                  <span className="picker-radio" />
                )}
              </div>
            );
          })}
        </div>
      </Popup>
    </div>
  );
};

export default QrPayPage;
