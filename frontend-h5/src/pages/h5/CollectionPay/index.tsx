import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Input, Radio, Toast, SpinLoading } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { AMOUNT_MIN, AMOUNT_MAX } from '@/constants';
import { AmountInput } from '@/components/h5/AmountInput';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import './index.less';

const CollectionPayPage: React.FC = () => {
  const { token } = useParams();
  const [loading, setLoading] = useState(true);
  const [locking, setLocking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [order, setOrder] = useState<collectionService.CollectionOrder | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [amount, setAmount] = useState(0);
  const [subject, setSubject] = useState('');
  const [password, setPassword] = useState('');
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'MINI_CREDIT' | null>(null);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);

  useEffect(() => {
    if (token) {
      loadOrder(token);
    }
  }, [token]);

  const loadOrder = async (t: string) => {
    try {
      // 付款人身份依赖登录态；未登录先跳登录页，登录成功后按 redirect 回到本页继续付款
      if (!localStorage.getItem('accessToken')) {
        history.replace(`/h5/login?redirect=${encodeURIComponent(`/h5/collection/pay/${t}`)}`);
        return;
      }
      // 必须先建立匿名引导会话再交换令牌：后端要求令牌交换与后续支付
      // 都发生在同一 bootstrap 会话中，直接交换会因缺少会话返回未授权
      await collectionService.bootstrapSession(t);
      const data = await collectionService.exchangeToken(t);
      setOrder(data);
      setLoading(false);
      const [accountResult, creditResult] = await Promise.allSettled([
        accountService.getMyAccount(),
        creditService.getCreditSummary(),
      ]);
      if (accountResult.status === 'fulfilled') setAccount(accountResult.value as unknown as accountService.AccountInfo);
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
      );

      // 3. 提交支付
      await collectionService.submitPayment(order.collectionOrderId, confirmationToken);

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

  return (
    <div className="collection-pay-page">
      <Card className="payee-card">
        <div className="payee-title">收款人</div>
        <div className="payee-name">{order.payeeName}</div>
      </Card>

      {draftStage ? (
        <>
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
            color="primary"
            loading={locking}
            onClick={handleLockAmount}
            style={{ marginTop: 12 }}
          >
            确认信息
          </Button>
        </Card>
        <Card className="funding-card">
          <div className="funding-title">选择支付方式</div>
          <Radio.Group value={fundingSource || undefined} onChange={(value) => {
            setFundingSource(value as 'BALANCE' | 'MINI_CREDIT');
            setPassword('');
          }}>
            <div className="funding-options">
              <div className="funding-option"><Radio value="BALANCE">余额支付（可用 {((account?.availableFen || 0) / 100).toFixed(2)} 元）</Radio></div>
              <div className="funding-option"><Radio value="MINI_CREDIT">Mini 花呗支付（可用 {((credit?.availableFen || 0) / 100).toFixed(2)} 元）</Radio></div>
            </div>
          </Radio.Group>
        </Card>
        </>
      ) : (
        <>
          <Card className="amount-card">
            <div className="amount-title">付款金额</div>
            <div className="amount-value">
              <AmountDisplay amountFen={order.amountFen || 0} size="large" />
            </div>
            {/* 固定码备注只能由收款人创建时填写，付款方只读；无备注时展示“无备注” */}
            <div className="amount-subject">备注：{subject || '无备注'}</div>
          </Card>

          <Card className="info-card">
            <div className="info-row">
              <span className="info-label">收款人</span>
              <span className="info-value">{order.payeeName}</span>
            </div>
            {order.payerName && (
              <div className="info-row">
                <span className="info-label">付款人</span>
                <span className="info-value">{order.payerName}</span>
              </div>
            )}
            <div className="info-row">
              <span className="info-label">手续费</span>
              <span className="info-value">免手续费</span>
            </div>
            <div className="info-row">
              <span className="info-label">实际扣款</span>
              <span className="info-value">
                <AmountDisplay amountFen={Math.round(amount * 100)} />
              </span>
            </div>
          </Card>

          <Card className="password-card">
            <div className="funding-title">选择支付方式</div>
            <Radio.Group value={fundingSource || undefined} onChange={(value) => {
              setFundingSource(value as 'BALANCE' | 'MINI_CREDIT');
              setPassword('');
            }}>
              <div className="funding-options">
                <div className="funding-option"><Radio value="BALANCE">余额支付（可用 {((account?.availableFen || 0) / 100).toFixed(2)} 元）</Radio></div>
                <div className="funding-option"><Radio value="MINI_CREDIT">Mini 花呗支付（可用 {((credit?.availableFen || 0) / 100).toFixed(2)} 元）</Radio></div>
              </div>
            </Radio.Group>
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
        </>
      )}

      {draftStage && (
        <div className="pay-actions">
          <Button block size="large" onClick={() => history.back()}>
            取消
          </Button>
        </div>
      )}
    </div>
  );
};

export default CollectionPayPage;
