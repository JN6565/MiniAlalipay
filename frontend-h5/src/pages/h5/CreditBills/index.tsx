import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { List, Toast, SpinLoading, Empty } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { BILL_STATUS_TEXT } from '@/constants';
import './index.less';

const CreditBillsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [bills, setBills] = useState<creditService.CreditBill[]>([]);

  useEffect(() => {
    loadBills();
  }, []);

  const loadBills = async () => {
    try {
      const data = await creditService.getBills();
      setBills(data);
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

  return (
    <div className="credit-bills-page">
      {bills.length === 0 ? (
        <Empty description="暂无账单" style={{ padding: '60px 0' }} />
      ) : (
        <List>
          {bills.map((bill) => (
            <List.Item
              key={bill.billId}
              description={
                <div className="bill-desc">
                  <span>{bill.period}</span>
                  <span className="bill-due">到期：{bill.dueAt}</span>
                </div>
              }
              extra={
                <div className="bill-extra">
                  <div className={`bill-status bill-status-${bill.status.toLowerCase()}`}>
                    {BILL_STATUS_TEXT[bill.status] || bill.status}
                  </div>
                  <div className="bill-amount">
                    应还：<AmountDisplay amountFen={bill.outstandingFen} size="small" />
                  </div>
                </div>
              }
              onClick={() => history.push(`/h5/credit/bills/${bill.billId}`)}
              clickable
            >
              <div className="bill-info">
                <div className="bill-total">
                  消费：<AmountDisplay amountFen={bill.totalFen} size="small" />
                </div>
                <div className="bill-paid">
                  已还：<AmountDisplay amountFen={bill.paidFen} size="small" />
                </div>
              </div>
            </List.Item>
          ))}
        </List>
      )}
    </div>
  );
};

export default CreditBillsPage;
