import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Card, Button, List, Toast, SpinLoading } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { BILL_STATUS_TEXT } from '@/constants';
import './index.less';

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
        creditService.getBills({ pageSize: 3 }),
      ]);
      setCredit(creditData);
      setBills(billsData.items || []);
    } catch (error) {
      Toast.show({ content: '加载失败', icon: 'fail' });
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

  return (
    <div className="credit-page">
      {/* 额度概览 */}
      <Card className="credit-card">
        <div className="credit-header">
          <div className="credit-title">Mini花呗</div>
          <div className={`credit-status credit-status-${credit?.status?.toLowerCase()}`}>
            {credit?.status === 'ACTIVE' ? '正常' : '已暂停'}
          </div>
        </div>

        <div className="credit-amount">
          <div className="amount-label">可用额度</div>
          <div className="amount-value">
            <AmountDisplay amountFen={credit?.availableFen || 0} size="large" />
          </div>
        </div>

        <div className="credit-details">
          <div className="detail-item">
            <div className="detail-label">总额度</div>
            <div className="detail-value">
              <AmountDisplay amountFen={credit?.totalLimitFen || 0} size="small" />
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">已用额度</div>
            <div className="detail-value">
              <AmountDisplay amountFen={credit?.usedFen || 0} size="small" />
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">冻结额度</div>
            <div className="detail-value">
              <AmountDisplay amountFen={credit?.frozenFen || 0} size="small" />
            </div>
          </div>
        </div>

        <div className="credit-notice">
          额度不计入虚拟余额，仅用于商户扫码支付
        </div>
      </Card>

      {/* 未出账金额 */}
      {credit?.unbilledFen ? (
        <Card className="unbilled-card">
          <div className="unbilled-row">
            <span className="unbilled-label">未出账金额</span>
            <span className="unbilled-value">
              <AmountDisplay amountFen={credit.unbilledFen} />
            </span>
          </div>
          <Button
            size="small"
            color="primary"
            onClick={() => history.push('/h5/credit/repay')}
          >
            提前还款
          </Button>
        </Card>
      ) : null}

      {/* 最近账单 */}
      <Card className="bills-card">
        <div className="bills-header">
          <span className="bills-title">最近账单</span>
          <a className="bills-link" onClick={() => history.push('/h5/credit/bills')}>
            查看全部
          </a>
        </div>

        {bills.length === 0 ? (
          <div className="empty-state">暂无账单</div>
        ) : (
          <List>
            {bills.map((bill) => (
              <List.Item
                key={bill.billId}
                description={bill.period}
                extra={
                  <span className={`bill-status bill-status-${bill.status.toLowerCase()}`}>
                    {BILL_STATUS_TEXT[bill.status] || bill.status}
                  </span>
                }
                onClick={() => history.push(`/h5/credit/bills/${bill.billId}`)}
                clickable
              >
                <div className="bill-amount">
                  应还：<AmountDisplay amountFen={bill.outstandingFen} size="small" />
                </div>
              </List.Item>
            ))}
          </List>
        )}
      </Card>
    </div>
  );
};

export default CreditPage;
