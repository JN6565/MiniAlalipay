import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { List, Toast, SpinLoading, Empty } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { BILL_STATUS_TEXT, BILLING_STATUS_TEXT } from '@/constants';
import { formatTime } from '@/utils/format';
import './index.less';

const CreditBillsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [bills, setBills] = useState<creditService.CreditBill[]>([]);
  const [unbilledPurchases, setUnbilledPurchases] = useState<creditService.CreditPurchase[]>([]);

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
      setBills(billsData);
      setUnbilledPurchases(purchasesData);
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
      {/* 未出账消费：当月花呗消费尚未生成账单，单独展示明细并提供提前还款入口 */}
      {unbilledPurchases.length > 0 ? (
        <div className="unbilled-section">
          <div className="unbilled-section-header">
            <span className="unbilled-section-title">未出账消费</span>
            <a className="unbilled-section-link" onClick={() => history.push('/h5/credit/repay')}>
              提前还款
            </a>
          </div>
          <List>
            {unbilledPurchases.map((purchase) => (
              <List.Item
                key={purchase.purchaseId}
                description={
                  <div className="bill-desc">
                    <span>{formatTime(purchase.occurredAt)}</span>
                  </div>
                }
                extra={
                  <div className="bill-extra">
                    <div className="bill-status bill-status-unbilled">
                      {BILLING_STATUS_TEXT[purchase.billingStatus] || purchase.billingStatus}
                    </div>
                    <div className="bill-amount">
                      消费：<AmountDisplay amountFen={purchase.amountFen} size="small" />
                    </div>
                  </div>
                }
              >
                <div className="bill-info">
                  <div className="bill-total">
                    扫码消费
                  </div>
                </div>
              </List.Item>
            ))}
          </List>
          <div className="unbilled-section-notice">未出账消费将于下月 1 日生成账单，到期日为账单月 10 日</div>
        </div>
      ) : null}

      {bills.length === 0 ? (
        unbilledPurchases.length > 0 ? (
          <Empty description="暂无已出账账单" style={{ padding: '40px 0' }} />
        ) : (
          <Empty description="暂无账单，本月消费将于下月 1 日出账" style={{ padding: '60px 0' }} />
        )
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
