import React, { useEffect, useState } from 'react';
import { Card, Tabs, Toast, SpinLoading } from 'antd-mobile';
import * as accountService from '@/services/account';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import './index.less';

const AnalyticsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<'7d' | '30d'>('7d');
  const [analytics, setAnalytics] = useState<accountService.AnalyticsData | null>(null);

  useEffect(() => {
    loadAnalytics();
  }, [range]);

  const loadAnalytics = async () => {
    try {
      const data = await accountService.getAnalytics(range);
      setAnalytics(data);
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
    <div className="analytics-page">
      <Tabs activeKey={range} onChange={(key) => setRange(key as any)}>
        <Tabs.Tab title="近7天" key="7d" />
        <Tabs.Tab title="近30天" key="30d" />
      </Tabs>

      <Card className="summary-card">
        <div className="summary-row">
          <div className="summary-item">
            <div className="summary-label">收入</div>
            <div className="summary-value income">
              <AmountDisplay amountFen={analytics?.incomeFen || 0} />
            </div>
          </div>
          <div className="summary-item">
            <div className="summary-label">支出</div>
            <div className="summary-value expense">
              <AmountDisplay amountFen={analytics?.expenseFen || 0} />
            </div>
          </div>
        </div>
      </Card>

      <Card className="chart-card">
        <div className="chart-title">收支趋势</div>
        <div className="chart-placeholder">
          图表功能开发中...
        </div>
      </Card>

      <Card className="payees-card">
        <div className="payees-title">交易对象分布</div>
        {analytics?.topPayees && analytics.topPayees.length > 0 ? (
          <div className="payees-list">
            {analytics.topPayees.map((payee, index) => (
              <div key={payee.userId} className="payee-item">
                <span className="payee-rank">{index + 1}</span>
                <span className="payee-name">{payee.nickname}</span>
                <span className="payee-amount">
                  <AmountDisplay amountFen={payee.totalFen} size="small" />
                </span>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty-state">暂无数据</div>
        )}
      </Card>
    </div>
  );
};

export default AnalyticsPage;
