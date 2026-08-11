import React, { useEffect, useRef, useState } from 'react';
import { Toast } from 'antd-mobile';
import { Axis, Canvas, Chart, Interval, Line, Point, Tooltip } from '@antv/f2';
import * as accountService from '@/services/account';
import { formatAmount } from '@/utils/format';
import { Skeleton, IconSet } from '@/components/h5/common';
import './index.less';

// F2 的 Canvas/Chart 是 F-engine 组件，仅用于构造 F2 渲染树，不由 React DOM 直接渲染。
const F2Canvas = Canvas as unknown as React.ComponentType<any>;
const F2Chart = Chart as unknown as React.ComponentType<any>;
const F2Axis = Axis as unknown as React.ComponentType<any>;
const F2Line = Line as unknown as React.ComponentType<any>;
const F2Point = Point as unknown as React.ComponentType<any>;
const F2Tooltip = Tooltip as unknown as React.ComponentType<any>;
const F2Interval = Interval as unknown as React.ComponentType<any>;

/**
 * 支出分类（V2.1 分析重设计）：仅统计 OUT 方向，按账本 memo 关键词归类。
 * 归类规则与账单列表图标保持一致；色板与设计稿环形图一致。
 */
const EXPENSE_CATEGORIES: { key: string; label: string; color: string; match: (memo: string) => boolean }[] = [
  { key: 'credit', label: '花呗', color: '#8b5cf6', match: (m) => /花呗|还款|消费/.test(m) },
  { key: 'qrpay', label: '扫码支付', color: '#256cff', match: (m) => /支付|收款|扫码/.test(m) },
  { key: 'transfer', label: '转账', color: '#18c0e8', match: (m) => m.includes('转账') },
  { key: 'fund', label: '充值提现', color: '#12b76a', match: (m) => /充值|提现/.test(m) },
  { key: 'other', label: '其他', color: '#94a3ba', match: () => true },
];

interface CategorySlice {
  label: string;
  color: string;
  amountFen: number;
  percent: number;
}

/**
 * 独立分析页（原账单页「分析」Tab，V2.1 设计完整保留）：
 * 收支汇总三卡 + 指标双卡（日均支出/收支比）+ 支出分类占比环形图 + 收支趋势折线 + 交易对象排行。
 * 汇总/趋势/交易对象来自后端分析接口；分类占比由前端拉取账本分录（limit 100）
 * 按 memo 归类聚合（后端未提供分类维度，不新增接口）。
 */
const AnalyticsPanel: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<'7d' | '30d'>('7d');
  const [analytics, setAnalytics] = useState<accountService.AnalyticsData | null>(null);
  const [slices, setSlices] = useState<CategorySlice[]>([]);

  useEffect(() => {
    let cancelled = false;
    const days = range === '7d' ? 7 : 30;
    const load = async () => {
      setLoading(true);
      try {
        // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
        const data = (await accountService.getAnalytics(range)) as unknown as accountService.AnalyticsData;
        // 分类占比：拉取分录后按时间窗 + OUT 方向 + memo 归类聚合
        // 同 getAnalytics：拦截器已拆包，运行时为业务数据而非 AxiosResponse
        const page = (await accountService.getTransactions({ pageSize: 100 })) as unknown as {
          items: accountService.Transaction[];
        };
        const since = Date.now() - days * 24 * 60 * 60 * 1000;
        const expenses = (page.items || []).filter(
          (tx) => tx.direction === 'OUT' && new Date(tx.createdAt).getTime() >= since,
        );
        const buckets = EXPENSE_CATEGORIES.map((c) => ({ ...c, amountFen: 0, matched: false }));
        for (const tx of expenses) {
          const memo = tx.memo?.trim() || '';
          // 顺序匹配首个命中的业务分类，未命中落到"其他"，保证每条分录只计入一个分类
          const bucket = buckets.find((b) => !b.matched && b.key !== 'other' && b.match(memo))
            || buckets.find((b) => b.key === 'other');
          if (bucket) {
            bucket.amountFen += tx.amountFen;
          }
        }
        const total = buckets.reduce((sum, b) => sum + b.amountFen, 0);
        if (!cancelled) {
          setAnalytics(data);
          setSlices(
            buckets
              .filter((b) => b.amountFen > 0)
              .map((b) => ({
                label: b.label,
                color: b.color,
                amountFen: b.amountFen,
                percent: total > 0 ? Math.round((b.amountFen / total) * 100) : 0,
              })),
          );
        }
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
  const days = range === '7d' ? 7 : 30;
  // 日均支出 = 区间支出 ÷ 天数（金额整数分，展示层转换）
  const dailyExpenseFen = Math.round(expenseFen / days);
  // 收支比 = 收入 ÷ 支出（保留一位小数）；支出为 0 时无意义显示 "--"
  const ratioText = expenseFen > 0 ? `${(incomeFen / expenseFen).toFixed(1)} : 1` : '--';
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

          {/* 指标双卡：日均支出与收支比 */}
          <div className="analytics-metrics">
            <div className="metric-cell">
              <div className="metric-icon">
                <IconSet name="clock" size={15} color="var(--h5-primary)" />
              </div>
              <div className="metric-body">
                <div className="metric-value">¥{formatAmount(dailyExpenseFen)}</div>
                <div className="metric-label">日均支出</div>
              </div>
            </div>
            <div className="metric-cell">
              <div className="metric-icon">
                <IconSet name="chart" size={15} color="var(--h5-primary)" />
              </div>
              <div className="metric-body">
                <div className="metric-value">{ratioText}</div>
                <div className="metric-label">收支比（收入 : 支出）</div>
              </div>
            </div>
          </div>

          {/* 支出分类占比：环形图 + 图例（仅 OUT 方向，前端按 memo 归类） */}
          <div className="analytics-card">
            <div className="analytics-card-title">
              <IconSet name="huabei" size={15} color="var(--h5-primary)" />
              支出分类占比
            </div>
            {slices.length > 0 ? (
              <div className="category-ratio">
                <CategoryDonut slices={slices} />
                <div className="category-legend">
                  {slices.map((slice) => (
                    <div key={slice.label} className="legend-row">
                      <i className="legend-color" style={{ background: slice.color }} />
                      <span className="legend-name">{slice.label}</span>
                      <span className="legend-amount">¥{formatAmount(slice.amountFen)}</span>
                      <span className="legend-percent">{slice.percent}%</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="chart-empty">暂无支出数据</div>
            )}
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

          {/* 交易对象排行 */}
          <div className="analytics-card">
            <div className="analytics-card-title plain">交易对象排行</div>
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

/**
 * 支出分类环形图：F2 polar 坐标系 + Interval 堆叠（innerRadius 形成环形）。
 * 颜色顺序与右侧图例一致；组件卸载时销毁 F2 实例。
 */
const CategoryDonut: React.FC<{ slices: CategorySlice[] }> = ({ slices }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const context = canvas.getContext('2d');
    if (!context) return undefined;
    const data = slices.map((slice) => ({ name: slice.label, amountFen: slice.amountFen }));
    const colors = slices.map((slice) => slice.color);
    const { props } = (
      <F2Canvas context={context} pixelRatio={window.devicePixelRatio || 1}>
        <F2Chart
          data={data}
          coord={{ type: 'polar', transposed: true, innerRadius: 0.62, radius: 0.9 }}
        >
          <F2Interval x="name" y="amountFen" color={['name', colors]} adjust="stack" />
        </F2Chart>
      </F2Canvas>
    );
    const chartCanvas = new Canvas(props);
    void chartCanvas.render();
    return () => chartCanvas.destroy();
  }, [slices]);

  return (
    <div className="category-donut" aria-label="支出分类占比环形图">
      <canvas ref={canvasRef} />
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
