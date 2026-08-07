import React, { useEffect, useState } from 'react';
import { history, useSearchParams, useLocation } from 'umi';
import { Card, Button, Toast, SpinLoading, Dialog } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import * as paymentPasswordService from '@/services/paymentPassword';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

/** 账号脱敏：保留前 4 位和后 4 位，中间用 **** 代替；短账号直接展示。 */
const maskAccount = (account: string) => {
  if (!account) return '';
  if (account.length <= 8) return account;
  return `${account.slice(0, 4)}****${account.slice(-4)}`;
};

const TransferConfirmPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const draftId = searchParams.get('draftId');
  // 转账页跳转时携带的收款人展示信息（后端草稿接口仅返回 payeeUserId）
  const navState = (location.state || {}) as {
    payeeNickname?: string;
    payeeAccountNumber?: string;
  };
  const [loading, setLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(true);
  const [draft, setDraft] = useState<transferService.TransferDraft | null>(null);
  const [password, setPassword] = useState('');
  // 校验通过后服务端返回的新版本，签发确认令牌必须使用它
  const [validatedVersion, setValidatedVersion] = useState<number | null>(null);

  useEffect(() => {
    if (draftId) {
      loadDraft();
    }
  }, [draftId]);

  const loadDraft = async () => {
    try {
      setDraftLoading(true);
      const draftData = await transferService.getDraft(draftId!);
      setDraft(draftData);

      // 风控预检；后端当前仅返回 PASS，不通过时抛异常由 catch 处理
      const riskResult = await transferService.validateDraft(draftId!, draftData.version);
      setValidatedVersion(riskResult.version);
    } catch (error: any) {
      Toast.show({ content: error.message || '加载草稿失败', icon: 'fail' });
      history.back();
    } finally {
      setDraftLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    if (!draft || validatedVersion === null) {
      Toast.show({ content: '草稿未就绪，请返回重试', icon: 'fail' });
      return;
    }

    // 大额转账二次确认
    if (draft.amountFen >= 500000) {
      const confirmed = await Dialog.confirm({
        content: `确认转账 ¥${(draft.amountFen / 100).toFixed(2)} 元？`,
        confirmText: '确认',
        cancelText: '取消',
      });
      if (!confirmed) return;
    }

    setLoading(true);
    try {
      // 1. 验证支付密码并签发一次性支付证明（TRANSFER_CONFIRM 用途）
      const { paymentProof } = await paymentPasswordService.issuePaymentProof(password);

      // 2. 用支付证明签发一次性确认令牌；令牌不得写入日志、URL 或浏览器存储
      const { confirmationToken } = await transferService.issueConfirmation(
        draftId!,
        paymentProof,
        validatedVersion,
      );

      // 3. 提交转账
      const result = await transferService.submitTransfer({
        draftId: draftId!,
        confirmationToken,
      });

      // 跳转到结果页；后端状态接口不含收款人昵称，通过路由 state 携带展示
      history.push(`/h5/transfer/result/${result.transactionId}`, {
        payeeNickname: navState.payeeNickname,
      });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '转账失败' });
    } finally {
      setLoading(false);
    }
  };

  if (!draftId) {
    return <div className="error-state">缺少草稿ID</div>;
  }

  if (draftLoading) {
    return (
      <div className="loading-container">
        <SpinLoading />
        <div className="loading-text">加载中...</div>
      </div>
    );
  }

  return (
    <div className="transfer-confirm-page">
      <Card className="confirm-card">
        <div className="confirm-title">请确认转账信息</div>

        <div className="confirm-row">
          <span className="confirm-label">付款方</span>
          <span className="confirm-value">我的账户</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">收款方</span>
          <span className="confirm-value">
            {navState.payeeNickname || draft?.payeeUserId || '-'}
            {navState.payeeAccountNumber && ` (${maskAccount(navState.payeeAccountNumber)})`}
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">金额</span>
          <span className="confirm-value amount">
            {draft && <AmountDisplay amountFen={draft.amountFen} size="large" />}
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">备注</span>
          <span className="confirm-value">{draft?.remark || '无'}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">手续费</span>
          <span className="confirm-value">¥0.00</span>
        </div>
      </Card>

      <Card className="password-card">
        <div className="password-title">请输入支付密码</div>
        <PasswordInput value={password} onChange={setPassword} length={6} />
      </Card>

      <div className="confirm-actions">
        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleConfirm}
          disabled={!password || password.length !== 6}
        >
          确认转账
        </Button>
        <Button block size="large" onClick={() => history.back()}>
          返回修改
        </Button>
      </div>
    </div>
  );
};

export default TransferConfirmPage;
