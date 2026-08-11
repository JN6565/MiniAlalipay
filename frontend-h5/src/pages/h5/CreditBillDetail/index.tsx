import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Toast, SpinLoading } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { formatBalance } from '@/services/bankCard';
import { IconSet } from '@/components/h5/common';
import { BILL_STATUS_TEXT } from '@/constants';
import { formatTime } from '@/utils/format';
import './index.less';

const CreditBillDetailPage: React.FC = () => {
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [bill, setBill] = useState<creditService.BillDetail | null>(null);

  useEffect(() => {
    if (id) {
      loadBill(id);
    }
  }, [id]);

  const loadBill = async (billId: string) => {
    try {
      const data = await creditService.getBillDetail(billId);
      setBill(data);
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

  if (!bill) {
    return <div className="error-state">账单不存在</div>;
  }

  return (
    <div className="credit-bill-detail-page">
      {/* 账单概览卡 */}
      <div className="cbd-card overview-card">
        <div className="overview-head">
          <div className="bill-period">{bill.period} 账单</div>
          <div className={`bill-status bill-status-${bill.status.toLowerCase()}`}>
            {BILL_STATUS_TEXT[bill.status] || bill.status}
          </div>
        </div>
        <div className="overview-cols">
          <div className="overview-col">
            <div className="col-label">消费总额</div>
            <div className="col-value">¥{formatBalance(bill.totalFen)}</div>
          </div>
          <div className="overview-col">
            <div className="col-label">已还金额</div>
            <div className="col-value paid">¥{formatBalance(bill.paidFen)}</div>
          </div>
          <div className="overview-col">
            <div className="col-label">剩余应还</div>
            <div className="col-value outstanding">¥{formatBalance(bill.outstandingFen)}</div>
          </div>
        </div>
        <div className="overview-dates">
          <span>出账日 {formatTime(bill.statementDate, 'YYYY-MM-DD')}</span>
          <span>到期日 {formatTime(bill.dueAt, 'YYYY-MM-DD')}</span>
        </div>
        <div className="bill-notice">费率：0（无利息、服务费或罚息）</div>
      </div>

      {/* 消费账单 */}
      <div className="cbd-card">
        <div className="cbd-section-title">消费账单</div>
        {bill.items && bill.items.length > 0 ? (
          bill.items.map((item, index) => (
            <div
              className="cbd-row"
              key={item.purchaseId}
              style={index === bill.items.length - 1 ? { borderBottom: 'none' } : undefined}
            >
              <span className="cbd-row-icon spend">
                <IconSet name="receipt" size={15} color="#7b6cff" />
              </span>
              <div className="cbd-row-main">
                <div className="cbd-row-title">{item.merchantName || '商户消费'}</div>
                <div className="cbd-row-sub">{formatTime(item.occurredAt, 'MM-DD HH:mm')}</div>
              </div>
              <span className="cbd-row-amount">-¥{formatBalance(item.amountFen)}</span>
            </div>
          ))
        ) : (
          <div className="cbd-empty">暂无消费账单</div>
        )}
      </div>

      {/* 还款记录 */}
      {bill.allocations && bill.allocations.length > 0 && (
        <div className="cbd-card">
          <div className="cbd-section-title">还款记录</div>
          {bill.allocations.map((allocation, index) => (
            <div
              className="cbd-row"
              key={allocation.repaymentId}
              style={index === bill.allocations.length - 1 ? { borderBottom: 'none' } : undefined}
            >
              <span className="cbd-row-icon repay">
                <IconSet name="check" size={15} color="var(--h5-success)" />
              </span>
              <div className="cbd-row-main">
                <div className="cbd-row-title">还款</div>
                <div className="cbd-row-sub">{formatTime(allocation.createdAt)}</div>
              </div>
              <span className="cbd-row-amount in">+¥{formatBalance(allocation.amountFen)}</span>
            </div>
          ))}
        </div>
      )}

      {/* 立即还款 */}
      {bill.status !== 'PAID' && (
        <div className="cbd-repay-cta" onClick={() => history.push('/h5/credit/repay')}>
          立即还款
        </div>
      )}
    </div>
  );
};

export default CreditBillDetailPage;
