import React, { useEffect, useRef, useState } from 'react';
import { Card, Tabs, Toast, SpinLoading } from 'antd-mobile';
import { Axis, Canvas, Chart, Line, Point, Tooltip, jsx } from '@antv/f2';
import * as accountService from '@/services/account';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import './index.less';

// F2 的 Canvas/Chart 是 F-engine 组件，仅用于构造 F2 渲染树，不由 React DOM 直接渲染。
const F2Canvas = Canvas as unknown as React.ComponentType<any>;
const F2Chart = Chart as unknown as React.ComponentType<any>;
const F2Axis = Axis as unknown as React.ComponentType<any>;
const F2Line = Line as unknown as React.ComponentType<any>;
const F2Point = Point as unknown as React.ComponentType<any>;
const F2Tooltip = Tooltip as unknown as React.ComponentType<any>;

const AnalyticsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<'7d' | '30d'>('7d');
  const [analytics, setAnalytics] = useState<accountService.AnalyticsData | null>(null);

  useEffect(() => {
    setLoading(true);
    loadAnalytics();
  }, [range]);

  const loadAnalytics = async () => {
    try {
      const data = (await accountService.getAnalytics(range)) as unknown as accountService.AnalyticsData;
      setAnalytics(data);
    } catch (error) {
      Toast.show({ content: '加载失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const trend = analytics?.trend ?? [];
  const hasTrendData = trend.some((point) => point.incomeFen > 0 || point.expenseFen > 0);

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
        {hasTrendData ? <IncomeExpenseChart trend={trend} /> : <div className="chart-empty">暂无收支数据</div>}
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

interface TrendPoint {
  date: string;
  incomeFen: number;
  expenseFen: number;
}

/** 使用账本趋势数据绘制收入、支出两条折线；组件卸载时销毁 F2 实例。 */
const IncomeExpenseChart: React.FC<{ trend: TrendPoint[] }> = ({ trend }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const context = canvas.getContext('2d');
    if (!context) return undefined;
    const data = trend.flatMap((point) => [
      { date: point.date.slice(5), type: '收入', amountFen: point.incomeFen },
      { date: point.date.slice(5), type: '支出', amountFen: point.expenseFen },
    ]);
    const { props } = (
      <F2Canvas context={context} pixelRatio={window.devicePixelRatio || 1}>
        <F2Chart data={data} scale={{ amountFen: { min: 0, nice: true } }}>
          <F2Axis field="date" tickCount={trend.length > 7 ? 5 : trend.length} />
          <F2Axis field="amountFen" tickCount={5} formatter={(value: unknown) => `¥${Number(value) / 100}`} />
          <F2Line x="date" y="amountFen" color={['type', ['#22a06b', '#e5484d']]} size={2} />
          <F2Point x="date" y="amountFen" color={['type', ['#22a06b', '#e5484d']]} size={2} />
          <F2Tooltip />
        </F2Chart>
      </F2Canvas>
    );
    const chartCanvas = new Canvas(props);
    void chartCanvas.render();
    return () => chartCanvas.destroy();
  }, [trend]);

  return (
    <div className="trend-chart" aria-label="收入支出趋势图">
      <canvas ref={canvasRef} />
      <div className="trend-legend" aria-hidden="true">
        <span><i className="legend-dot income-dot" />收入</span>
        <span><i className="legend-dot expense-dot" />支出</span>
      </div>
    </div>
  );
};

export default AnalyticsPage;
