import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { Card, Button, Toast, SpinLoading } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import * as paymentPasswordService from '@/services/paymentPassword';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

const TransferConfirmPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const draftId = searchParams.get('draftId');
  const [loading, setLoading] = useState(false);
  const [draft, setDraft] = useState<transferService.TransferDraft | null>(null);
  const [password, setPassword] = useState('');

  useEffect(() => {
    // TODO: 加载草稿详情
  }, [draftId]);

  const handleConfirm = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      // 1. 校验支付密码，获取确认令牌
      const { confirmationToken } = await paymentPasswordService.verifyPaymentPassword({
        paymentPassword: password,
        subjectType: 'TRANSFER_DRAFT',
        subjectId: draftId!,
      });

      // 2. 提交转账
      const result = await transferService.submitTransfer({
        draftId: draftId!,
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

  return (
    <div className="transfer-confirm-page">
      <Card className="confirm-card">
        <div className="confirm-title">确认转账</div>

        <div className="confirm-row">
          <span className="confirm-label">付款账户</span>
          <span className="confirm-value">我的账户</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">收款人</span>
          <span className="confirm-value">{draft?.payeeNickname || '-'}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">转账金额</span>
          <span className="confirm-value">
            {draft && <AmountDisplay amountFen={draft.amountFen} />}
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
