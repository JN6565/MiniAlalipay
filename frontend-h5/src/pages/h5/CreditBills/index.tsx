import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Toast, SpinLoading } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { formatBalance } from '@/services/bankCard';
import { IconSet, EmptyState } from '@/components/h5/common';
import { BILL_STATUS_TEXT, BILLING_STATUS_TEXT } from '@/constants';
import { formatTime } from '@/utils/format';
import './index.less';

const UNBILLED = 'UNBILLED';

/**
 * 花呗账单页（V2）：月份切换胶囊 + 月汇总（消费总额/已还/待还）+ 消费列表。
 * 「未出账」胶囊展示当月尚未出账的消费明细（每月 1 日出账）。
 */
const CreditBillsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [bills, setBills] = useState<creditService.CreditBill[]>([]);
  const [unbilledPurchases, setUnbilledPurchases] = useState<creditService.CreditPurchase[]>([]);
  const [selected, setSelected] = useState<string>('');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      // 并行拉取已出账月度账单与未出账消费明细；
      // 账单每月 1 日生成，当月消费在出账前只能通过消费明细查看
      const [billsData, purchasesData] = await Promise.all([
        creditService.getBills(),
        creditService.getPurchases('UNBILLED'),
      ]);
      const billList = (billsData as unknown as creditService.CreditBill[]) || [];
      const purchaseList = (purchasesData as unknown as creditService.CreditPurchase[]) || [];
      setBills(billList);
      setUnbilledPurchases(purchaseList);
      // 默认选中最近一期账单；无账单时降级选中未出账
      setSelected(billList.length > 0 ? billList[0].period : UNBILLED);
    } catch (error: any) {
      Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  const chips: Array<{ key: string; label: string }> = [
    ...bills.map((b) => ({ key: b.period, label: b.period })),
    ...(unbilledPurchases.length > 0 ? [{ key: UNBILLED, label: '未出账' }] : []),
  ];

  const selectedBill = bills.find((b) => b.period === selected) || null;
  const unbilledTotalFen = unbilledPurchases.reduce((sum, p) => sum + (p.amountFen || 0), 0);

  // 月汇总三列：消费总额 / 已还款 / 待还款
  const summary = selectedBill
    ? { total: selectedBill.totalFen, paid: selectedBill.paidFen, outstanding: selectedBill.outstandingFen }
    : { total: unbilledTotalFen, paid: 0, outstanding: unbilledTotalFen };

  return (
    <div className="credit-bills-page">
      {/* 月份切换胶囊 */}
      {chips.length > 0 && (
        <div className="cb-chips">
          {chips.map((chip) => (
            <span
              key={chip.key}
              className={`cb-chip${selected === chip.key ? ' active' : ''}`}
              onClick={() => setSelected(chip.key)}
            >
              {chip.label}
            </span>
          ))}
        </div>
      )}

      {/* 月汇总卡 */}
      {chips.length > 0 && (
        <div className="cb-summary">
          <div className="summary-item">
            <div className="summary-label">消费总额</div>
            <div className="summary-value">¥{formatBalance(summary.total)}</div>
          </div>
          <div className="summary-item">
            <div className="summary-label">已还款</div>
            <div className="summary-value paid">¥{formatBalance(summary.paid)}</div>
          </div>
          <div className="summary-item">
            <div className="summary-label">待还款</div>
            <div className="summary-value outstanding">¥{formatBalance(summary.outstanding)}</div>
          </div>
        </div>
      )}

      {chips.length === 0 ? (
        <EmptyState
          icon={<IconSet name="bill" size={30} color="var(--h5-text-3)" />}
          text="暂无账单，本月消费将于下月 1 日出账"
        />
      ) : selected === UNBILLED ? (
        <>
          {/* 未出账消费明细 */}
          <div className="cb-card">
            {unbilledPurchases.map((purchase, index) => (
              <div
                className="cb-row"
                key={purchase.purchaseId}
                style={index === unbilledPurchases.length - 1 ? { borderBottom: 'none' } : undefined}
              >
                <span className="cb-row-icon">
                  <IconSet name="receipt" size={15} color="#7b6cff" />
                </span>
                <div className="cb-row-main">
                  <div className="cb-row-title">扫码消费</div>
                  <div className="cb-row-sub">
                    {formatTime(purchase.occurredAt, 'MM-DD HH:mm')} ·{' '}
                    {BILLING_STATUS_TEXT[purchase.billingStatus] || purchase.billingStatus}
                  </div>
                </div>
                <span className="cb-row-amount">-¥{formatBalance(purchase.amountFen)}</span>
              </div>
            ))}
          </div>
          <div className="cb-notice">
            未出账消费将于下月 1 日生成账单，到期日为账单月 10 日
            <span className="cb-notice-link" onClick={() => history.push('/h5/credit/repay')}>
              提前还款
            </span>
          </div>
        </>
      ) : selectedBill ? (
        /* 已出账账单行：状态 + 到期日 + 应还，点击进账单详情 */
        <div className="cb-card">
          <div className="cb-row" onClick={() => history.push(`/h5/credit/bills/${selectedBill.billId}`)}>
            <span className="cb-row-icon">
              <IconSet name="bill" size={15} color="#7b6cff" />
            </span>
            <div className="cb-row-main">
              <div className="cb-row-title">{selectedBill.period} 账单</div>
              <div className="cb-row-sub">
                到期：{formatTime(selectedBill.dueAt, 'YYYY-MM-DD')} ·{' '}
                {BILL_STATUS_TEXT[selectedBill.status] || selectedBill.status}
              </div>
            </div>
            <span className="cb-row-amount">
              应还 ¥{formatBalance(selectedBill.outstandingFen)}
            </span>
            <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default CreditBillsPage;
