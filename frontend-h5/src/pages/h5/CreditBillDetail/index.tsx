import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, List, Button, Toast, SpinLoading } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
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
      {/* 账单概览 */}
      <Card className="bill-card">
        <div className="bill-header">
          <div className="bill-period">{bill.period}</div>
          <div className={`bill-status bill-status-${bill.status.toLowerCase()}`}>
            {BILL_STATUS_TEXT[bill.status] || bill.status}
          </div>
        </div>

        <div className="bill-amounts">
          <div className="amount-row">
            <span className="amount-label">消费总额</span>
            <span className="amount-value">
              <AmountDisplay amountFen={bill.totalFen} />
            </span>
          </div>
          <div className="amount-row">
            <span className="amount-label">已还金额</span>
            <span className="amount-value">
              <AmountDisplay amountFen={bill.paidFen} />
            </span>
          </div>
          <div className="amount-row">
            <span className="amount-label">剩余应还</span>
            <span className="amount-value highlight">
              <AmountDisplay amountFen={bill.outstandingFen} />
            </span>
          </div>
        </div>

        <div className="bill-dates">
          <div className="date-row">
            <span className="date-label">出账日</span>
            <span className="date-value">{formatTime(bill.statementDate, 'YYYY-MM-DD')}</span>
          </div>
          <div className="date-row">
            <span className="date-label">到期日</span>
            <span className="date-value">{formatTime(bill.dueAt, 'YYYY-MM-DD')}</span>
          </div>
        </div>

        <div className="bill-notice">费率：0（无利息、服务费或罚息）</div>
      </Card>

      {/* 消费明细 */}
      <Card className="items-card">
        <div className="items-title">消费明细</div>
        {bill.items && bill.items.length > 0 ? (
          <List>
            {bill.items.map((item) => (
              <List.Item
                key={item.purchaseId}
                description={formatTime(item.occurredAt)}
                extra={
                  <AmountDisplay amountFen={item.amountFen} size="small" />
                }
              >
                {item.merchantName || '商户消费'}
              </List.Item>
            ))}
          </List>
        ) : (
          <div className="empty-state">暂无消费明细</div>
        )}
      </Card>

      {/* 还款记录 */}
      {bill.allocations && bill.allocations.length > 0 && (
        <Card className="allocations-card">
          <div className="allocations-title">还款记录</div>
          <List>
            {bill.allocations.map((allocation) => (
              <List.Item
                key={allocation.repaymentId}
                description={formatTime(allocation.createdAt)}
                extra={
                  <AmountDisplay amountFen={allocation.amountFen} size="small" />
                }
              >
                还款
              </List.Item>
            ))}
          </List>
        </Card>
      )}

      {/* 操作按钮 */}
      {bill.status !== 'PAID' && (
        <div className="bill-actions">
          <Button
            block
            color="primary"
            onClick={() => history.push('/h5/credit/repay')}
          >
            立即还款
          </Button>
        </div>
      )}
    </div>
  );
};

export default CreditBillDetailPage;
