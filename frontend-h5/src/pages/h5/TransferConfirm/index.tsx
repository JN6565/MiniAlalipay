import React, { useEffect, useState } from 'react';
import { history, useSearchParams, useLocation } from 'umi';
import { Card, Button, Toast, SpinLoading, Divider } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as userService from '@/services/user';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import SecurePasswordInput from '@/components/h5/SecurePasswordInput';
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
  // 付款方为当前登录用户，通过 /api/v1/users/me 获取本人账号用于展示
  const [myAccount, setMyAccount] = useState<string>('');

  // 加载草稿详情
  useEffect(() => {
    if (!draftId) return;

    const loadDraft = async () => {
      setDraftLoading(true);
      try {
        const result = await transferService.getDraft(draftId);
        setDraft(result);
      } catch (error: any) {
        Toast.show({ icon: 'fail', content: error.message || '草稿不存在' });
        history.back();
      } finally {
        setDraftLoading(false);
      }
    };
    loadDraft();
  }, [draftId]);

  // 加载本人账号信息，失败不阻断页面，付款账户降级展示为“我的账户”
  useEffect(() => {
    userService
      .getMyInfo()
      .then((info) => setMyAccount(info.accountNumber))
      .catch(() => setMyAccount(''));
  }, []);

  const handleConfirm = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    if (!draft) {
      Toast.show({ content: '草稿未加载', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      // 1. 校验草稿（风控预检）
      const validationResult = await transferService.validateDraft(draft.draftId, draft.version);
      if (validationResult.result !== 'PASS') {
        Toast.show({ content: '风控校验未通过', icon: 'fail' });
        return;
      }

      // 2. 验证支付密码，获取支付证明
      const { paymentProof } = await paymentPasswordService.verifyPaymentPassword(password);

      // 3. 签发确认令牌
      const { confirmationToken } = await transferService.issueConfirmation(
        draft.draftId,
        paymentProof,
        validationResult.version
      );

      // 4. 提交转账
      const result = await transferService.submitTransfer({
        draftId: draft.draftId,
        confirmationToken,
      });

      history.push(`/h5/transfer/result/${result.transactionId}`);
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
      <div className="loading-state">
        <SpinLoading color="primary" />
        <div>加载中...</div>
      </div>
    );
  }

  if (!draft) {
    return <div className="error-state">草稿不存在或已过期</div>;
  }

  return (
    <div className="transfer-confirm-page">
      <Card className="confirm-card">
        <div className="confirm-title">确认转账</div>

        <div className="confirm-row">
          <span className="confirm-label">付款账户</span>
          <span className="confirm-value">
            {myAccount ? `我的账户 ${maskAccount(myAccount)}` : '我的账户'}
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">收款人</span>
          <span className="confirm-value">{navState.payeeNickname || draft.payeeUserId}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">收款账号</span>
          <span className="confirm-value">{maskAccount(navState.payeeAccountNumber || '') || '-'}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">转账金额</span>
          <span className="confirm-value">
            <AmountDisplay amountFen={draft.amountFen} />
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">备注</span>
          <span className="confirm-value">{draft.remark || '无'}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">手续费</span>
          <span className="confirm-value">¥0.00</span>
        </div>
      </Card>

      <Card className="password-card">
        <div className="password-title">请输入支付密码</div>
        <SecurePasswordInput
          value={password}
          onChange={setPassword}
          length={6}
          onComplete={(value) => {
            console.log('密码输入完成:', value);
          }}
        />
      </Card>

      <div className="confirm-actions">
        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleConfirm}
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
