import {
  AlertOutlined,
  CheckCircleFilled,
  ReloadOutlined,
  RiseOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Alert, Badge, Button, Card, Col, Empty, Row, Skeleton, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo } from 'react';
import type { DashboardServiceHealth, OpsTransactionItem } from '@/services/ops';
import { getDashboardSummary } from '@/services/ops';
import { formatAmountFen } from '@/utils/amount';
import { qualityRuleLabel, qualityTaskLabel } from '@/utils/opsLabels';
import pageStyles from '../page.less';
import styles from './index.less';

/** 统一交易业务类型的运营展示名称。 */
const BUSINESS_TYPE_LABELS: Record<string, string> = {
  TRANSFER: '转账',
  QR_PAY: '余额扫码支付',
  CREDIT_PAY: '花呗扫码支付',
  CREDIT_REPAY: '信用还款',
  RECHARGE: '模拟充值',
  REFUND: '受控退款',
};

/** 将万分比转换为展示百分比；只在展示边界计算，不参与任何金额运算。 */
function formatBps(bps: number): string {
  const integer = Math.floor(bps / 100);
  const decimal = String(bps % 100).padStart(2, '0');
  return `${integer}.${decimal}%`;
}

/** 质量结果的通过率仅由服务端返回的检查数和失败数计算。 */
function formatQualityRate(checkedCount: number, failedCount: number): string {
  if (checkedCount <= 0) return '—';
  const passedBps = Math.max(0, Math.floor((checkedCount - failedCount) * 10_000 / checkedCount));
  return formatBps(passedBps);
}

/** 服务健康标签颜色只反映探针证据，不把 UNKNOWN 误显示为正常。 */
function healthColor(status: DashboardServiceHealth['status']): string {
  return status === 'UP' ? 'success' : status === 'DOWN' ? 'error' : 'default';
}

/** 统一交易状态的运营展示标签。 */
function transactionStatus(status: string): { text: string; color: string } {
  const mappings: Record<string, { text: string; color: string }> = {
    SUCCESS: { text: '成功', color: 'success' },
    PROCESSING: { text: '处理中', color: 'processing' },
    COMPENSATING: { text: '补偿中', color: 'warning' },
    MANUAL_REVIEW: { text: '人工复核', color: 'warning' },
    REVERSED: { text: '已冲正', color: 'default' },
    CANCELLED: { text: '已取消', color: 'default' },
  };
  return mappings[status] ?? { text: status, color: 'default' };
}

/** 最近 60 分钟的真实交易事件微型趋势，不存在事件时呈现空刻度而非模拟走势。 */
function ActivityBars({ values }: { values: number[] }) {
  const max = Math.max(...values, 1);
  return (
    <div className={styles.activityBars} aria-label="最近 60 分钟交易活动趋势">
      {values.map((value, index) => (
        <span key={index} style={{ height: `${value === 0 ? 14 : Math.max(14, Math.round(value / max * 100))}%` }} />
      ))}
    </div>
  );
}

/**
 * 可信运行看板。
 *
 * <p>页面只读取服务端聚合的运营投影，不自行汇总分页交易、复制资金事实或伪造服务健康状态；
 * 任一汇总请求失败时保留明确的未知状态并允许手动重新加载。</p>
 */
export default function Dashboard() {
  const summaryQuery = useQuery({
    queryKey: ['ops', 'dashboard-summary'],
    queryFn: getDashboardSummary,
    refetchInterval: 30_000,
    retry: 1,
  });
  const summary = summaryQuery.data?.data;
  const trendValues = useMemo(() => {
    const buckets = Array.from({ length: 12 }, () => 0);
    for (const point of summary?.transactionTrend ?? []) {
      const index = Math.min(11, Math.max(0, Math.floor(dayjs(point.bucketAt).minute() / 5)));
      buckets[index] += point.value;
    }
    return buckets;
  }, [summary?.transactionTrend]);

  const transactionColumns: ColumnsType<OpsTransactionItem> = [
    {
      title: '交易号', dataIndex: 'transactionId', key: 'transactionId',
      width: 300,
      align: 'center',
      render: (value: string) => <Typography.Text className={styles.transactionId}>{value}</Typography.Text>,
    },
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150, render: (value: string) => dayjs(value).format('HH:mm:ss') },
    {
      title: '方式', dataIndex: 'businessType', key: 'businessType',
      width: 180,
      render: (value: string) => BUSINESS_TYPE_LABELS[value] ?? '其他交易',
    },
    { title: '金额', dataIndex: 'amountFen', key: 'amountFen', width: 160, render: (value: number) => <strong>¥ {formatAmountFen(value)}</strong> },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      width: 130,
      render: (value: string) => {
        const state = transactionStatus(value);
        return <Tag color={state.color}>{state.text}</Tag>;
      },
    },
  ];

  if (summaryQuery.isLoading) {
    return <div className={pageStyles.page}><Skeleton active paragraph={{ rows: 12 }} /></div>;
  }

  return (
    <div className={`${pageStyles.page} ${styles.compact}`}>
      <div className={styles.heading}>
        <Typography.Text type="secondary">
          {summary ? `更新于 ${dayjs(summary.generatedAt).format('HH:mm:ss')}     运营只读投影` : '看板数据暂不可用'}
        </Typography.Text>
      </div>

      {summaryQuery.isError && (
        <Alert
          type="warning"
          showIcon
          message="看板汇总数据加载失败"
          description="当前不展示零值或模拟状态，请确认网关与后端服务已启动后重新加载。"
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => summaryQuery.refetch()}>重新加载</Button>}
        />
      )}

      {summary && <>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.metricCard}>
              <Typography.Text type="secondary">今日交易额</Typography.Text>
              <div className={styles.metricValue}>¥ {formatAmountFen(summary.kpis.todayTransactionAmountFen)}</div>
              <div className={styles.metricFooter}><RiseOutlined /> 最近 60 分钟交易活动</div>
              <ActivityBars values={trendValues} />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.metricCard}>
              <Typography.Text type="secondary">支付成功率</Typography.Text>
              <div className={styles.metricValue}>{formatBps(summary.kpis.paymentSuccessRateBps)}</div>
              <div className={styles.metricFooter}><CheckCircleFilled /> 已收敛交易口径</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.metricCard}>
              <Typography.Text type="secondary">待人工确认</Typography.Text>
              <div className={styles.metricValue}>{summary.kpis.pendingManualCaseCount}</div>
              <div className={styles.metricFooter}><SafetyCertificateOutlined /> OPEN、CLAIMED 工单</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.metricCard}>
              <Typography.Text type="secondary">活动告警</Typography.Text>
              <div className={`${styles.metricValue} ${summary.kpis.openAlertCount > 0 ? styles.alertValue : ''}`}>{summary.kpis.openAlertCount}</div>
              <div className={styles.metricFooter}><AlertOutlined /> 待处置 OPEN 告警</div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={14}>
            <section className={styles.panel} aria-label="数据质量">
              <div className={styles.panelHeader}><Typography.Title level={5}>数据质量</Typography.Title><Typography.Text type="secondary">对账窗口 · T+1</Typography.Text></div>
              {summary.dataQuality.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="上一自然日暂无质量结果" /> : (
                <div className={styles.qualityList}>
                  {summary.dataQuality.map((item) => {
                    const rate = formatQualityRate(item.checkedCount, item.failedCount);
                    const failed = item.status === 'FAILED';
                    return <div className={styles.qualityRow} key={item.resultId}>
                      <div>
                        <strong title={item.taskCode}>{qualityTaskLabel(item.taskCode)}</strong>
                        <Typography.Text type="secondary" title={item.ruleCode}>
                          {qualityRuleLabel(item.ruleCode)}
                        </Typography.Text>
                      </div>
                      <div className={styles.qualityProgress}><span className={failed ? styles.progressFailure : ''} style={{ width: rate === '—' ? '0%' : rate }} /></div>
                      <Tag color={failed ? 'error' : item.status === 'WARNING' ? 'warning' : 'success'}>{failed ? '不可用' : rate}</Tag>
                    </div>;
                  })}
                </div>
              )}
            </section>
          </Col>
          <Col xs={24} xl={10}>
            <section className={styles.panel} aria-label="服务健康">
              <div className={styles.panelHeader}><Typography.Title level={5}>服务健康</Typography.Title><Typography.Text type="secondary">最近探针</Typography.Text></div>
              <div className={styles.serviceList}>
                {summary.services.map((service) => (
                  <div className={styles.serviceRow} key={service.serviceCode}>
                    <Badge status={healthColor(service.status) as 'success' | 'error' | 'default'} text={service.serviceName} />
                    <Typography.Text type="secondary">{service.probeLatencyMs === null ? '未知' : `${service.probeLatencyMs}ms`}</Typography.Text>
                  </div>
                ))}
              </div>
            </section>
          </Col>
        </Row>

        <section className={styles.transactions} aria-label="最近交易">
          <div className={styles.panelHeader}>
            <Typography.Title level={5}>最近交易</Typography.Title>
            <Button type="link" onClick={() => history.push('/admin/transactions')}>查看全部</Button>
          </div>
          <Table<OpsTransactionItem> rowKey="transactionId" columns={transactionColumns} dataSource={summary.recentTransactions} pagination={false} size="small" tableLayout="fixed" />
        </section>
      </>}
    </div>
  );
}
