import React, { useEffect, useRef, useState } from 'react';
import { Toast } from 'antd-mobile';
import { Axis, Canvas, Chart, Line, Point, Tooltip } from '@antv/f2';
import * as accountService from '@/services/account';
import { formatAmount } from '@/utils/format';
import { Skeleton, IconSet } from '@/components/h5/common';

// F2 的 Canvas/Chart 是 F-engine 组件，仅用于构造 F2 渲染树，不由 React DOM 直接渲染。
const F2Canvas = Canvas as unknown as React.ComponentType<any>;
const F2Chart = Chart as unknown as React.ComponentType<any>;
const F2Axis = Axis as unknown as React.ComponentType<any>;
const F2Line = Line as unknown as React.ComponentType<any>;
const F2Point = Point as unknown as React.ComponentType<any>;
const F2Tooltip = Tooltip as unknown as React.ComponentType<any>;

/**
 * 账单页「分析」Tab：由原独立资产分析页迁移整合而来。
 * 内容：收支汇总三卡（收入/支出/结余）+ 收支趋势折线 + 交易对象分布占比条。
 */
const AnalyticsPanel: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<'7d' | '30d'>('7d');
  const [analytics, setAnalytics] = useState<accountService.AnalyticsData | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
        const data = (await accountService.getAnalytics(range)) as unknown as accountService.AnalyticsData;
        if (!cancelled) setAnalytics(data);
      } catch (error: any) {
        if (!cancelled) {
          Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [range]);

  const trend = analytics?.trend ?? [];
  const hasTrendData = trend.some((p) => p.incomeFen > 0 || p.expenseFen > 0);
  const incomeFen = analytics?.incomeFen || 0;
  const expenseFen = analytics?.expenseFen || 0;
  const balanceFen = incomeFen - expenseFen;
  const topPayees = analytics?.topPayees ?? [];
  const maxPayeeFen = topPayees.reduce((max, p) => Math.max(max, p.totalFen), 0);

  return (
    <div className="analytics-panel">
      {/* 时间范围切换 */}
      <div className="tx-chips">
        {([['7d', '近7天'], ['30d', '近30天']] as const).map(([key, label]) => (
          <span
            key={key}
            className={`tx-chip${range === key ? ' active' : ''}`}
            onClick={() => setRange(key)}
          >
            {label}
          </span>
        ))}
      </div>

      {loading ? (
        <>
          <Skeleton variant="card" height={70} />
          <div style={{ marginTop: 10 }}>
            <Skeleton variant="card" height={200} />
          </div>
        </>
      ) : (
        <>
          {/* 收支汇总三卡 */}
          <div className="analytics-summary">
            <div className="summary-cell">
              <div className="summary-label">{range === '7d' ? '近7天' : '近30天'}收入</div>
              <div className="summary-value in">¥{formatAmount(incomeFen)}</div>
            </div>
            <div className="summary-cell">
              <div className="summary-label">{range === '7d' ? '近7天' : '近30天'}支出</div>
              <div className="summary-value out">¥{formatAmount(expenseFen)}</div>
            </div>
            <div className="summary-cell">
              <div className="summary-label">结余</div>
              <div className={`summary-value ${balanceFen >= 0 ? 'balance' : 'out'}`}>
                {balanceFen >= 0 ? '+' : '−'}¥{formatAmount(Math.abs(balanceFen))}
              </div>
            </div>
          </div>

          {/* 收支趋势 */}
          <div className="analytics-card">
            <div className="analytics-card-title">
              <IconSet name="chart" size={15} color="var(--h5-primary)" />
              收支趋势
            </div>
            {hasTrendData ? (
              <IncomeExpenseChart trend={trend} />
            ) : (
              <div className="chart-empty">暂无收支数据</div>
            )}
          </div>

          {/* 交易对象分布 */}
          <div className="analytics-card">
            <div className="analytics-card-title plain">交易对象分布</div>
            {topPayees.length > 0 ? (
              topPayees.map((payee, index) => {
                const percent = maxPayeeFen > 0 ? Math.round((payee.totalFen / maxPayeeFen) * 100) : 0;
                return (
                  <div key={payee.userId} className="payee-bar">
                    <div className="payee-bar-head">
                      <span className="payee-name">{index + 1}. {payee.nickname}</span>
                      <span className="payee-amount">¥{formatAmount(payee.totalFen)}</span>
                    </div>
                    <div className="payee-bar-track">
                      <div
                        className="payee-bar-fill"
                        style={{ width: `${Math.max(percent, 4)}%` }}
                      />
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="chart-empty">暂无数据</div>
            )}
          </div>
        </>
      )}
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
          <F2Line x="date" y="amountFen" color={['type', ['#256cff', '#9db8e0']]} size={2} />
          <F2Point x="date" y="amountFen" color={['type', ['#256cff', '#9db8e0']]} size={2} />
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

export default AnalyticsPanel;
