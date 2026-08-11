import React, { useEffect, useState } from 'react';
import { useParams, history, useLocation } from 'umi';
import { Input, Toast, SpinLoading, Popup } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { AMOUNT_MIN, AMOUNT_MAX } from '@/constants';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
import { AmountInput } from '@/components/h5/AmountInput';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { IconSet } from '@/components/h5/common';
import './index.less';

const CollectionPayPage: React.FC = () => {
  const { token } = useParams();
  const location = useLocation();
  // 短码兑换路径：兑换端点已建单并绑定会话，路由参数是订单 ID，
  // 无需再走 bootstrap/令牌交换，直接按 ID 查单。
  const viaShortCode = new URLSearchParams(location.search).get('via') === 'short-code';
  const [loading, setLoading] = useState(true);
  const [locking, setLocking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [order, setOrder] = useState<collectionService.CollectionOrder | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [amount, setAmount] = useState(0);
  const [subject, setSubject] = useState('');
  const [password, setPassword] = useState('');
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'BANK_CARD' | 'MINI_CREDIT' | null>(null);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [bankCards, setBankCards] = useState<BankCard[]>([]);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [showCardPicker, setShowCardPicker] = useState(false);

  useEffect(() => {
    if (token) {
      loadOrder(token);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const loadOrder = async (t: string) => {
    try {
      // 付款人身份依赖登录态；未登录先跳登录页，登录成功后按 redirect 回到本页继续付款
      if (!localStorage.getItem('accessToken')) {
        const current = `/h5/collection/pay/${t}${viaShortCode ? '?via=short-code' : ''}`;
        history.replace(`/h5/login?redirect=${encodeURIComponent(current)}`);
        return;
      }
      let data: collectionService.CollectionOrder;
      if (viaShortCode) {
        // 短码兑换已在服务端完成建单与会话绑定，此处 t 为订单 ID
        data = await collectionService.getOrderStatus(t) as unknown as collectionService.CollectionOrder;
      } else {
        // 必须先建立匿名引导会话再交换令牌：后端要求令牌交换与后续支付
        // 都发生在同一 bootstrap 会话中，直接交换会因缺少会话返回未授权
        await collectionService.bootstrapSession(t);
        data = await collectionService.exchangeToken(t);
      }
      setOrder(data);
      const [accountResult, bankCardsResult, creditResult] = await Promise.allSettled([
        accountService.getMyAccount(),
        getBankCards(),
        creditService.getCreditSummary(),
      ]);
      if (accountResult.status === 'fulfilled') setAccount(accountResult.value as unknown as accountService.AccountInfo);
      if (bankCardsResult.status === 'fulfilled') setBankCards(bankCardsResult.value as unknown as BankCard[]);
      if (creditResult.status === 'fulfilled') setCredit(creditResult.value as unknown as creditService.CreditSummary);
      if (data.amountFen) {
        setAmount(data.amountFen / 100);
      }
      if (data.subject) {
        setSubject(data.subject);
      }
    } catch (error: any) {
      setErrorMessage(error.message || '订单无效或已过期');
    } finally {
      setLoading(false);
    }
  };

  const handleLockAmount = async () => {
    if (!order) return;
    if (!fundingSource) {
      Toast.show({ content: '请选择支付方式', icon: 'fail' });
      return;
    }
    if (amount < AMOUNT_MIN || amount > AMOUNT_MAX) {
      Toast.show({ content: `金额范围 ${AMOUNT_MIN}-${AMOUNT_MAX} 元`, icon: 'fail' });
      return;
    }

    setLocking(true);
    try {
      // 返回的订单是锁定后的权威事实：版本 +1、金额与备注不可再修改，
      // 后续签发确认令牌必须使用该版本
      const locked = await collectionService.lockOrderAmount(order.collectionOrderId, {
        version: order.version || 0,
        amountFen: Math.round(amount * 100),
        subject,
      });
      setOrder(locked);
      if (locked.amountFen) {
        setAmount(locked.amountFen / 100);
      }
      if (locked.subject) {
        setSubject(locked.subject);
      }
      Toast.show({ icon: 'success', content: '金额已锁定' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '锁定失败' });
    } finally {
      setLocking(false);
    }
  };

  const handlePay = async () => {
    if (!order) return;
    if (!fundingSource) {
      Toast.show({ content: '请选择支付方式', icon: 'fail' });
      return;
    }
    if (fundingSource === 'BANK_CARD' && !selectedCardId) {
      Toast.show({ content: '请选择付款银行卡', icon: 'fail' });
      return;
    }
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    setSubmitting(true);
    try {
      // 1. 用支付密码向 user-center 申请一次性证明令牌
      const { paymentProof } = await paymentPasswordService.issuePaymentProof(
        password,
        'COLLECTION_CONFIRM',
      );

      // 2. 用证明令牌生成确认令牌；版本必须使用订单最新版本
      const { confirmationToken } = await collectionService.createOrderConfirmation(
        order.collectionOrderId,
        paymentProof,
        order.version || 0,
        fundingSource,
        fundingSource === 'BANK_CARD' ? selectedCardId || undefined : undefined,
      );

      // 3. 提交支付
      await collectionService.submitPayment(
        order.collectionOrderId,
        confirmationToken,
        fundingSource === 'BANK_CARD' ? selectedCardId || undefined : undefined,
      );

      // 结果页按订单 ID 查询状态，不能传交易 ID
      history.push(`/h5/collection/result/${order.collectionOrderId}`);
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
    return <div className="error-state">{errorMessage || '订单无效或已过期'}</div>;
  }

  // 个人码草稿阶段：先填写金额与备注并锁定，锁定后才进入支付
  const draftStage = order.editable;

  const selectedCard = bankCards.find((card) => card.cardId === selectedCardId) || null;
  const creditUnavailableReason = (() => {
    if (!credit?.opened) return '未开通，点击开通';
    if (credit.status !== 'ACTIVE') return credit.overdueFen > 0 ? '逾期暂停使用' : '当前不可用';
    if (!order.creditPayAllowed) return order.creditPayDisabledReason || '该收款码暂不支持花呗';
    if ((credit.availableFen || 0) < Math.round(amount * 100)) return '可用额度不足';
    return '';
  })();

  const selectFundingSource = (value: 'BALANCE' | 'BANK_CARD' | 'MINI_CREDIT') => {
    if (value === 'MINI_CREDIT') {
      if (!credit?.opened) {
        Toast.show({ content: '请先开通 Mini 花呗', icon: 'fail' });
        history.push('/h5/credit/open');
        return;
      }
      if (creditUnavailableReason) {
        Toast.show({ content: creditUnavailableReason, icon: 'fail' });
        return;
      }
    }
    setFundingSource(value);
    setPassword('');
    if (value === 'BANK_CARD') {
      if (bankCards.length === 0) {
        Toast.show({ content: '暂无银行卡，请先绑定', icon: 'fail' });
        history.push('/h5/bank-cards');
        return;
      }
      if (!selectedCardId) setSelectedCardId(bankCards[0].cardId);
      setShowCardPicker(true);
    }
  };

  const fundingRows: Array<{
    key: 'BALANCE' | 'BANK_CARD' | 'MINI_CREDIT';
    label: string;
    value: string;
    disabled?: boolean;
  }> = [
    { key: 'BALANCE', label: '账户余额', value: `可用 ¥${formatBalance(account?.availableFen || 0)}` },
    {
      key: 'BANK_CARD',
      label: '银行卡',
      value: selectedCard ? `${selectedCard.bankName}（${selectedCard.cardLast4}）` : '选择银行卡',
    },
    {
      key: 'MINI_CREDIT',
      label: 'Mini 花呗',
      value: creditUnavailableReason || `可用额度 ¥${formatBalance(credit?.availableFen || 0)}`,
      disabled: !!creditUnavailableReason && credit?.opened,
    },
  ];

  const renderFundingRows = () => (
    <div className="cp-card funding-rows">
      {fundingRows.map((row) => {
        const on = fundingSource === row.key;
        return (
          <div
            className={`funding-row${row.disabled ? ' disabled' : ''}`}
            key={row.key}
            onClick={() => selectFundingSource(row.key)}
          >
            <span className="funding-label">{row.label}</span>
            <span className="funding-value">{row.value}</span>
            <span className={`funding-radio${on ? ' active' : ''}`}>
              {on && <span className="funding-radio-dot" />}
            </span>
          </div>
        );
      })}
    </div>
  );

  return (
    <div className="collection-pay-page">
      <div className="cp-hero" />

      <div className="cp-body">
        {/* 收款方信息卡 */}
        <div className="cp-card payee-card">
          <div className="payee-tile">
            <IconSet name="collect" size={18} color="#fff" />
          </div>
          <div className="payee-name">收款方：{order.payeeName}</div>
          {!draftStage && <div className="payee-subject">备注：{subject || '无备注'}</div>}
        </div>

        {draftStage ? (
          <>
            {/* 草稿阶段：金额与备注可编辑，确认后锁定 */}
            <div className="cp-card amount-card">
              <div className="amount-label">填写金额</div>
              <AmountInput value={amount} onChange={setAmount} placeholder="0.00" />
              <div className="amount-divider" />
              <Input
                placeholder="备注（选填）"
                value={subject}
                onChange={setSubject}
                maxLength={50}
                className="amount-subject-input"
              />
            </div>
            {renderFundingRows()}
            <div
              className={`h5-btn-gradient cp-submit${locking ? ' disabled' : ''}`}
              onClick={() => !locking && handleLockAmount()}
            >
              {locking ? '锁定中...' : '确认信息'}
            </div>
          </>
        ) : (
          <>
            {/* 锁定阶段：金额只读 */}
            <div className="cp-card amount-card">
              <div className="amount-label">支付金额</div>
              <div className="amount-value">¥{formatBalance(order.amountFen || 0)}</div>
              <div className="amount-locked-tips">固定金额收款码，金额不可修改</div>
            </div>
            {renderFundingRows()}
            <div className="cp-card password-card">
              <div className="password-title">请输入支付密码</div>
              <PasswordInput value={password} onChange={setPassword} length={6} />
            </div>
            <div
              className={`h5-btn-gradient cp-submit${submitting || !fundingSource ? ' disabled' : ''}`}
              onClick={() => !submitting && handlePay()}
            >
              {submitting ? '提交中...' : '确认付款'}
            </div>
          </>
        )}

        <div className="cp-cancel" onClick={() => history.back()}>
          取消
        </div>
      </div>

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

export default CollectionPayPage;
