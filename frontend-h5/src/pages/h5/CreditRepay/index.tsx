import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { Skeleton, IconSet } from '@/components/h5/common';
import * as creditService from '@/services/credit';
import * as paymentPasswordService from '@/services/paymentPassword';
import * as accountService from '@/services/account';
import { formatBalance } from '@/services/bankCard';
import { AmountInput } from '@/components/h5/AmountInput';
import { PasswordInput } from '@/components/h5/PasswordInput';
import './index.less';

/**
 * 花呗还款页：加载信用欠款与余额账户后默认全额还款，支持修改金额。
 * 流程为「创建还款草稿（服务端预拆分账单）→ 输入 6 位支付密码 → 提交还款」，
 * 密码仅在提交时随请求发送，不写入本地存储；金额内部统一按分（amountFen）计算，
 * 仅展示层换算为元。
 */
const CreditRepayPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [amount, setAmount] = useState(0);
  const [editingAmount, setEditingAmount] = useState(false);
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
      const creditInfo = creditData as unknown as creditService.CreditSummary;
      const accountInfo = accountData as unknown as accountService.AccountInfo;
      setCredit(creditInfo);
      setAccount(accountInfo);

      // 默认全额还款
      const maxAmount = Math.min(
        creditInfo.usedFen,
        accountInfo.availableFen,
      );
      setAmount(maxAmount / 100);
    } catch (error: any) {
      Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
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

      // 2. 验证支付密码并签发还款用途的一次性支付证明（verify 仅校验不签发证明，必须走 proof 接口）
      const { paymentProof } = await paymentPasswordService.issuePaymentProof(password, 'CREDIT_REPAY');

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
      <div className="credit-repay-page">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    );
  }

  return (
    <div className="credit-repay-page">
      {/* 还款金额卡：默认全部待还，可点「修改金额」编辑 */}
      <div className="cr-card amount-card">
        <div className="amount-label">本月待还</div>
        {editingAmount ? (
          <>
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
            <div className="amount-edit-tips">
              最高可还 ¥{formatBalance(Math.min(credit?.usedFen || 0, account?.availableFen || 0))}
            </div>
            <div className="amount-edit-done" onClick={() => setEditingAmount(false)}>
              完成
            </div>
          </>
        ) : (
          <>
            <div className="amount-value">¥{formatBalance(Math.round(amount * 100))}</div>
            <div className="amount-edit-pill" onClick={() => setEditingAmount(true)}>
              修改金额
            </div>
          </>
        )}
      </div>

      {/* 支付方式卡（还款仅支持账户余额） */}
      <div className="cr-card method-card">
        <div className="method-title">支付方式</div>
        <div className="method-row active">
          <IconSet name="wallet" size={16} color="var(--h5-primary)" />
          <div className="method-info">
            <div className="method-label">账户余额</div>
            <div className="method-sub">¥{formatBalance(account?.availableFen || 0)}</div>
          </div>
          <span className="method-radio active" />
        </div>
        <div className="method-tips">银行卡还款暂未开放，请使用账户余额</div>
      </div>

      {/* 分配预览 */}
      {allocation.length > 0 && (
        <div className="cr-card allocation-card">
          <div className="method-title">分配预览</div>
          {allocation.map((item: any, index: number) => (
            <div key={index} className="allocation-item">
              <span className="allocation-type">
                {item.targetType === 'OVERDUE_BILL'
                  ? '逾期账单'
                  : item.targetType === 'BILL'
                    ? '已出账账单'
                    : '未出账消费'}
              </span>
              <span className="allocation-amount">¥{formatBalance(item.amountFen)}</span>
            </div>
          ))}
        </div>
      )}

      {/* 支付密码卡 */}
      <div className="cr-card password-card">
        <div className="password-title">请输入支付密码</div>
        <PasswordInput value={password} onChange={setPassword} length={6} />
      </div>

      {/* 确认还款 */}
      <div
        className={`credit-repay-cta${submitting ? ' disabled' : ''}`}
        onClick={() => !submitting && handleRepay()}
      >
        {submitting ? '提交中...' : `确认还款 ¥${formatBalance(Math.round(amount * 100))}`}
      </div>
      <div className="cr-cancel" onClick={() => history.back()}>
        取消
      </div>
    </div>
  );
};

export default CreditRepayPage;
