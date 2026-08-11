import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { formatAmount } from '@/utils/format';
import { Skeleton, IconSet } from '@/components/h5/common';
import { BILL_STATUS_TEXT } from '@/constants';
import './index.less';

/**
 * Mini 花呗首页（V2）：credit 渐变沉浸头部（本月应还大字 + 还款日 + 额度行）+
 * 悬浮白卡入口（账单/还款）+ 立即还款渐变按钮 + 最近账单列表。
 * 冻结额度不展示（全局约定）；额度不计入虚拟余额，仅用于商户扫码支付。
 */
const CreditPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [bills, setBills] = useState<creditService.CreditBill[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [creditData, billsData] = await Promise.all([
        creditService.getCreditSummary(),
        creditService.getBills(),
      ]);
      // 请求拦截器已拆包 ApiResponse，运行时返回业务数据
      setCredit(creditData as unknown as creditService.CreditSummary);
      setBills((billsData as unknown as creditService.CreditBill[]).slice(0, 3));
    } catch (error: any) {
      Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="credit-page">
        <Skeleton variant="card" height={220} />
        <div style={{ marginTop: 14 }}>
          <Skeleton variant="list" rows={3} />
        </div>
      </div>
    );
  }

  const billedFen = credit?.billedFen || 0;
  const duePayable = billedFen > 0 || (credit?.unbilledFen || 0) > 0;

  // 还款日：取最近一张未还清账单的到期日（M月D日）
  const dueBill = bills.find((b) => b.status === 'OPEN' || b.status === 'OVERDUE' || b.status === 'PARTIALLY_PAID');
  const dueDayText = (() => {
    if (!dueBill?.dueAt) return '';
    const d = new Date(dueBill.dueAt);
    if (Number.isNaN(d.getTime())) return '';
    return `${d.getMonth() + 1}月${d.getDate()}日还款`;
  })();

  return (
    <div className="credit-page">
      {/* 沉浸头部：本月应还 + 还款日 + 额度行 */}
      <div className="credit-hero">
        <div className="credit-hero-head">
          <span className="credit-title">Mini 花呗</span>
          {credit?.status !== 'ACTIVE' && <span className="credit-status">已暂停</span>}
        </div>
        <div className="credit-due">
          <div className="due-label">本月应还（元）</div>
          <div className="due-value">{formatAmount(billedFen)}</div>
          {dueDayText && (
            <div className="due-day">
              <IconSet name="clock" size={12} color="rgba(255,255,255,0.85)" />
              {dueDayText}
            </div>
          )}
        </div>
        <div className="credit-limit-cols">
          <div className="limit-item">
            <span className="limit-label">总额度</span>
            <span className="limit-value">¥{formatAmount(credit?.totalLimitFen || 0)}</span>
          </div>
          <div className="limit-item">
            <span className="limit-label">可用额度</span>
            <span className="limit-value">¥{formatAmount(credit?.availableFen || 0)}</span>
          </div>
        </div>
      </div>

      {/* 悬浮入口卡 + 立即还款 */}
      <div className="credit-body">
        <div className="credit-entry-card">
          <div className="entry-row" onClick={() => history.push('/h5/credit/bills')}>
            <span className="entry-icon">
              <IconSet name="receipt" size={16} color="var(--h5-primary)" />
            </span>
            <span className="entry-label">花呗账单</span>
            <span className="entry-value">查看每月消费账单</span>
            <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
          </div>
          <div className="entry-row" onClick={() => history.push('/h5/credit/repay')}>
            <span className="entry-icon">
              <IconSet name="wallet" size={16} color="var(--h5-primary)" />
            </span>
            <span className="entry-label">立即还款</span>
            <span className="entry-value">支持余额/银行卡</span>
            <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
          </div>
        </div>

        <div
          className={`credit-repay-cta${!duePayable ? ' disabled' : ''}`}
          onClick={() => duePayable && history.push('/h5/credit/repay')}
        >
          {billedFen > 0 ? '立即还款' : '提前还款'}
        </div>
        <div className="credit-notice">额度不计入虚拟余额，仅用于商户扫码支付</div>

        {/* 最近账单 */}
        <div className="bills-card">
          <div className="bills-header">
            <span className="bills-title">最近账单</span>
            <span className="bills-link" onClick={() => history.push('/h5/credit/bills')}>
              查看全部
            </span>
          </div>

          {bills.length === 0 ? (
            credit?.unbilledFen ? (
              // 有未出账消费但尚未生成月度账单（每月 1 日出账）：
              // 提示出账规则并引导到账单页查看未出账明细，避免误以为消费丢失
              <div className="empty-state">
                <div>本月消费暂未出账，将于下月 1 日生成账单</div>
                <span className="empty-state-link" onClick={() => history.push('/h5/credit/bills')}>
                  查看未出账单
                </span>
              </div>
            ) : (
              <div className="empty-state">暂无账单</div>
            )
          ) : (
            <div className="bills-list">
              {bills.map((bill) => (
                <div
                  className="bill-row"
                  key={bill.billId}
                  onClick={() => history.push(`/h5/credit/bills/${bill.billId}`)}
                >
                  <div className="bill-main">
                    <div className="bill-period">{bill.period}</div>
                    <div className="bill-due">应还 ¥{formatAmount(bill.outstandingFen)}</div>
                  </div>
                  <span className={`bill-status bill-status-${bill.status.toLowerCase()}`}>
                    {BILL_STATUS_TEXT[bill.status] || bill.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CreditPage;
