import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Card, Button, Toast, SpinLoading } from 'antd-mobile';
import * as creditService from '@/services/credit';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { AmountInput } from '@/components/h5/AmountInput';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

const CreditRepayPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [amount, setAmount] = useState(0);
  const [password, setPassword] = useState('');
  const [allocation, setAllocation] = useState<any[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [creditData, accountData] = await Promise.all([
        creditService.getCreditSummary(),
        accountService.getMyAccount(),
      ]);
      setCredit(creditData);
      setAccount(accountData);

      // 默认全额还款
      const maxAmount = Math.min(
        creditData.usedFen,
        accountData.availableFen,
      );
      setAmount(maxAmount / 100);
    } catch (error) {
      Toast.show({ content: '加载失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const handleCreateDraft = async () => {
    if (amount < 0.01) {
      Toast.show({ content: '还款金额不能小于0.01元', icon: 'fail' });
      return;
    }

    const amountFen = Math.round(amount * 100);
    if (amountFen > (credit?.usedFen || 0)) {
      Toast.show({ content: '还款金额不能超过应还金额', icon: 'fail' });
      return;
    }

    if (amountFen > (account?.availableFen || 0)) {
      Toast.show({ content: '余额不足', icon: 'fail' });
      return;
    }

    try {
      const draft = await creditService.createRepaymentDraft(amountFen);
      setAllocation(draft.allocation || []);
      return draft;
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
      return null;
    }
  };

  const handleRepay = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    setSubmitting(true);
    try {
      // 1. 创建还款草稿
      const draft = await handleCreateDraft();
      if (!draft) return;

      // 2. 验证支付密码并签发还款用途的一次性支付证明
      const { paymentProof } = await paymentPasswordService.verifyPaymentPassword({
        paymentPassword: password,
        purpose: 'CREDIT_REPAY',
      });

      // 3. 提交还款
      const result = await creditService.submitRepayment({
        repaymentDraftId: draft.repaymentDraftId,
        paymentProofToken: paymentProof,
      });

      Toast.show({ icon: 'success', content: '还款成功' });
      history.push('/h5/credit');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '还款失败' });
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

  return (
    <div className="credit-repay-page">
      {/* 账户信息 */}
      <Card className="info-card">
        <div className="info-row">
          <span className="info-label">虚拟余额</span>
          <span className="info-value">
            <AmountDisplay amountFen={account?.availableFen || 0} />
          </span>
        </div>
        <div className="info-row">
          <span className="info-label">信用应收</span>
          <span className="info-value">
            <AmountDisplay amountFen={credit?.usedFen || 0} />
          </span>
        </div>
        <div className="info-row">
          <span className="info-label">建议还款</span>
          <span className="info-value highlight">
            <AmountDisplay amountFen={credit?.usedFen || 0} />
          </span>
        </div>
      </Card>

      {/* 还款金额 */}
      <Card className="amount-card">
        <div className="amount-title">还款金额</div>
        <AmountInput
          value={amount}
          onChange={setAmount}
          min={0.01}
          max={Math.min(
            (credit?.usedFen || 0) / 100,
            (account?.availableFen || 0) / 100,
          )}
          placeholder="请输入还款金额"
        />
      </Card>

      {/* 分配预览 */}
      {allocation.length > 0 && (
        <Card className="allocation-card">
          <div className="allocation-title">分配预览</div>
          <div className="allocation-list">
            {allocation.map((item, index) => (
              <div key={index} className="allocation-item">
                <span className="allocation-type">
                  {item.targetType === 'OVERDUE_BILL'
                    ? '逾期账单'
                    : item.targetType === 'BILL'
                    ? '已出账账单'
                    : '未出账消费'}
                </span>
                <span className="allocation-amount">
                  <AmountDisplay amountFen={item.amountFen} size="small" />
                </span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* 支付密码 */}
      <Card className="password-card">
        <div className="password-title">请输入支付密码</div>
        <PasswordInput value={password} onChange={setPassword} length={6} />
      </Card>

      {/* 操作按钮 */}
      <div className="repay-actions">
        <Button
          block
          color="primary"
          size="large"
          loading={submitting}
          onClick={handleRepay}
        >
          确认还款
        </Button>
        <Button block size="large" onClick={() => history.back()}>
          取消
        </Button>
      </div>
    </div>
  );
};

export default CreditRepayPage;
