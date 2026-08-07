import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { Card, Button, Toast, SpinLoading, Dialog } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import * as paymentPasswordService from '@/services/paymentPassword';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

const TransferConfirmPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const draftId = searchParams.get('draftId');
  const [loading, setLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(true);
  const [draft, setDraft] = useState<transferService.TransferDraft | null>(null);
  const [password, setPassword] = useState('');

  useEffect(() => {
    if (draftId) {
      loadDraft();
    }
  }, [draftId]);

  const loadDraft = async () => {
    try {
      setDraftLoading(true);
      const draftData = await transferService.getDraft(draftId!);
      // 风控预检
      const riskResult = await transferService.validateDraft(draftId!, draftData.version);
      setDraft({ ...draftData, version: riskResult.version });
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

    // 大额转账二次确认
    if (draft && draft.amountFen >= 500000) {
      const confirmed = await Dialog.confirm({
        content: `确认转账 ¥${(draft.amountFen / 100).toFixed(2)} 元？`,
        confirmText: '确认',
        cancelText: '取消',
      });
      if (!confirmed) return;
    }

    setLoading(true);
    try {
      // 1. 校验支付密码，获取支付凭证
      const { paymentProof } = await paymentPasswordService.verifyPaymentPassword({
        paymentPassword: password,
        purpose: 'TRANSFER_CONFIRM',
      });

      // 2. 生成确认令牌
      const { confirmationToken } = await transferService.createConfirmation({
        subjectType: 'TRANSFER_DRAFT',
        subjectId: draftId!,
        subjectVersion: draft!.version,
        paymentProof,
      });

      // 3. 提交转账
      const result = await transferService.submitTransfer({
        draftId: draftId!,
        confirmationToken,
      });

      // 跳转到结果页
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
            {draft?.payeeNickname || '-'}
            {draft?.payeeAccountMasked && ` (${draft.payeeAccountMasked})`}
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
