import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { List, Tabs, Toast, SpinLoading, Empty } from 'antd-mobile';
import * as accountService from '@/services/account';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { formatTime, formatRelativeTime } from '@/utils/format';
import './index.less';

const TransactionsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [transactions, setTransactions] = useState<accountService.Transaction[]>([]);
  const [direction, setDirection] = useState<'ALL' | 'IN' | 'OUT'>('ALL');
  const [page, setPage] = useState(1);

  useEffect(() => {
    loadTransactions();
  }, [direction, page]);

  const loadTransactions = async () => {
    try {
      const params: any = { page, pageSize: 20 };
      if (direction !== 'ALL') {
        params.direction = direction;
      }
      const data = await accountService.getTransactions(params);
      setTransactions(data.items || []);
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
    <div className="transactions-page">
      <Tabs activeKey={direction} onChange={(key) => setDirection(key as any)}>
        <Tabs.Tab title="全部" key="ALL" />
        <Tabs.Tab title="收入" key="IN" />
        <Tabs.Tab title="支出" key="OUT" />
      </Tabs>

      {transactions.length === 0 ? (
        <Empty description="暂无交易记录" style={{ padding: '60px 0' }} />
      ) : (
        <List>
          {transactions.map((tx) => (
            <List.Item
              key={tx.transactionId}
              description={formatRelativeTime(tx.createdAt)}
              extra={
                <AmountDisplay
                  amountFen={tx.amountFen}
                  direction={tx.direction}
                  showSign
                  size="small"
                />
              }
              onClick={() => {
                // 只有普通转账已有业务详情接口，充值等账本事实暂不伪装成转账查询。
                if (tx.memo?.includes('转账')) {
                  history.push(`/h5/transfer/result/${tx.transactionId}`);
                }
              }}
              clickable={tx.memo?.includes('转账')}
            >
              <div className="tx-info">
                <div className="tx-counterparty">
                  {accountService.getLedgerEntryTitle(tx)}
                </div>
                <div className="tx-type">{tx.memo?.includes('充值') ? '充值' : '资金变动'}</div>
              </div>
            </List.Item>
          ))}
        </List>
      )}
    </div>
  );
};

export default TransactionsPage;
