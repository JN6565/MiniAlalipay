import { history, useAccess } from '@umijs/max';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Alert, Button, Card, Col, DatePicker, Descriptions, Empty, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { GatewayRequestError } from '@/services/request';
import { generateDailyReport, generateDailyReportPreview, getDailyReportDetail, listDailyReports, listMetricDefinitions, type DailyMetricItem, type DailyReportDetail, type DailyReportGeneration, type DailyReportPreview } from '@/services/ops';
import pageStyles from '../page.less';
import styles from './index.less';
import { formatAmountFen } from '@/utils/amount';
import { qualityRuleLabel } from '@/utils/opsLabels';

/** 临时报表质量门禁的运营中文说明。 */
const QUALITY_CHECK_LABEL: Record<string, string> = {
  INBOX_COMPLETE: '事件消费未完成',
  EVENT_QUARANTINE_EMPTY: '隔离事件',
};

const QUALITY_STATUS_LABEL: Record<string, string> = {
  PASSED: '通过',
  PREVIEW: '临时预览',
  PUBLISHED: '已发布',
  BLOCKED: '质量门禁阻断',
};

const QUALITY_STATUS_COLOR: Record<string, string> = {
  PASSED: 'success',
  PREVIEW: 'processing',
  PUBLISHED: 'success',
  BLOCKED: 'error',
};

const DATA_SOURCE_LABEL: Record<string, string> = {
  'metrics_db.analytics_event': '交易分析事件',
  'metrics_db.monitoring_transaction_final_projection': '最终交易投影',
  'metrics_db.quality_result': '数据质量检查',
  'metrics_db.monitor_alert': '运营告警',
};

const RECONCILIATION_TYPE_LABEL: Record<string, string> = {
  AMOUNT_MISMATCH: '金额不一致',
  MISSING_LEDGER_ENTRY: '缺少账本分录',
  MISSING_TRANSACTION: '缺少交易记录',
  DUPLICATE_ENTRY: '重复分录',
};

const ALERT_LEVEL_LABEL: Record<string, string> = {
  P0: '一级',
  P1: '二级',
  P2: '三级',
  CRITICAL: '紧急',
  WARNING: '警告',
  INFO: '提示',
};

const OPERATING_STATUS_LABEL: Record<string, string> = {
  OPEN: '待处理',
  ACKNOWLEDGED: '已确认',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  PASSED: '通过',
  FAILED: '失败',
  SUCCESS: '成功',
  PROCESSING: '处理中',
  REVERSED: '已冲正',
  CANCELLED: '已取消',
};

/**
 * T+1 报表页面。
 *
 * 读取通过质量门禁的真实日报指标，并按指标口径定义展示中文名称与单位，只读展示；
 * 指标值为整数，不做前端金额换算。
 */
export default function Reports() {
  const { message } = App.useApp();
  const access = useAccess();
  const [reportDate, setReportDate] = useState<string>(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
  const [preview, setPreview] = useState<DailyReportPreview>();
  const [generation, setGeneration] = useState<DailyReportGeneration>();

  const reportsQuery = useQuery({
    queryKey: ['ops', 'daily-reports', reportDate],
    queryFn: () => listDailyReports(reportDate),
  });

  const detailQuery = useQuery({
    queryKey: ['ops', 'daily-report-detail', reportDate],
    queryFn: () => getDailyReportDetail(reportDate),
    enabled: !preview,
  });

  // 指标口径定义用于将 metricCode 渲染为中文名称与单位；定义缺失时退回原始编码。
  const definitionsQuery = useQuery({
    queryKey: ['ops', 'metric-definitions'],
    queryFn: () => listMetricDefinitions(),
  });

  const definitionByCode = new Map(
    (definitionsQuery.data?.data ?? []).map((item) => [item.metricCode, item]),
  );

  const previewMutation = useMutation({
    mutationFn: generateDailyReportPreview,
    onSuccess: ({ data }) => {
      setPreview(data);
      setGeneration(undefined);
      message[data.status === 'READY' ? 'success' : 'warning'](
        data.status === 'READY' ? '临时报表已生成' : '临时报表未通过质量门禁',
      );
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '临时报表生成失败');
    },
  });

  const generateMutation = useMutation({
    mutationFn: () => generateDailyReport(reportDate),
    onSuccess: ({ data }) => {
      setPreview(undefined);
      setGeneration(data);
      reportsQuery.refetch();
      detailQuery.refetch();
      message[data.status === 'PUBLISHED' ? 'success' : 'warning'](
        data.status === 'PUBLISHED' ? '正式日报已生成' : '正式日报被质量门禁阻断，请先处理失败事件',
      );
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '正式日报生成失败');
    },
  });

  const previewRange = preview
    ? `${dayjs(preview.windowStart).format('YYYY-MM-DD HH:mm:ss')} 至 ${dayjs(preview.windowEnd).format('YYYY-MM-DD HH:mm:ss')}`
    : undefined;
  const displayedReports: DailyMetricItem[] = preview
    ? preview.metrics.map((metric) => ({
      ...metric,
      reportDate: previewRange ?? '',
      qualityStatus: 'PREVIEW',
    }))
    : detailQuery.data?.data?.metrics ?? reportsQuery.data?.data ?? [];
  const detail = detailQuery.data?.data;
  const failedQualityChecks = preview?.status === 'BLOCKED'
    ? preview.qualityChecks.filter((check) => check.status === 'FAILED')
    : [];
  const failedGenerationChecks = generation?.status === 'BLOCKED'
    ? generation.qualityChecks.filter((check) => check.status === 'FAILED')
    : [];

  const columns: ColumnsType<DailyMetricItem> = [
    {
      title: '指标名称',
      key: 'metricName',
      render: (_, record) => definitionByCode.get(record.metricCode)?.name ?? '未定义指标',
    },
    { title: preview ? '数据范围' : '数据日期', dataIndex: 'reportDate' },
    {
      title: '指标值',
      dataIndex: 'value',
      render: (value: number, record) => {
        const unit = definitionByCode.get(record.metricCode)?.unit;
        return unit ? `${value} ${unit}` : value;
      },
    },
    { title: '口径版本', dataIndex: 'metricVersion' },
    {
      title: '质量状态',
      dataIndex: 'qualityStatus',
      render: (value: string) => (
        <Tag color={QUALITY_STATUS_COLOR[value] ?? 'default'}>
          {QUALITY_STATUS_LABEL[value] ?? '未知状态'}
        </Tag>
      ),
    },
  ];

  const reportStatus = preview
    ? preview.status === 'READY' ? '临时预览' : '质量门禁阻断'
    : generation
      ? generation.status === 'PUBLISHED' ? '已发布' : '质量门禁阻断'
      : reportsQuery.data?.data?.length ? '已发布' : '尚未发布';
  const reportStatusColor = reportStatus === '质量门禁阻断' ? 'error' : reportStatus === '尚未发布' ? 'default' : 'success';
  const overview = detail?.overview;
  const trendMax = Math.max(...(detail?.transactionTrend ?? []).map((point) => point.transactionCount), 1);
  const formatBps = (value: number) => `${value >= 0 ? '+' : ''}${(value / 100).toFixed(2)}%`;
  const qualityConclusion = (value: string) => value === 'PASSED' ? '通过' : value === 'FAILED' ? '失败' : '未知';
  const statusColor = (value: string) => value === 'PASSED' || value === 'RESOLVED' || value === 'CLOSED' ? 'success' : value === 'FAILED' || value === 'OPEN' ? 'error' : 'warning';
  const dataSourceLabel = (value: string) => DATA_SOURCE_LABEL[value] ?? '其他运营数据';
  const reconciliationTypeLabel = (value: string) => RECONCILIATION_TYPE_LABEL[value] ?? '其他差异';
  const alertLevelLabel = (value: string) => ALERT_LEVEL_LABEL[value] ?? '其他级别';
  const operatingStatusLabel = (value: string) => OPERATING_STATUS_LABEL[value] ?? '未知状态';

  return (
    <main className={pageStyles.page}>
      <PageHeader extra={<Tag color={reportStatusColor}>{reportStatus}</Tag>} />
      <section className={pageStyles.toolbar} aria-label="报表筛选">
        <Typography.Text>数据日期</Typography.Text>
        <DatePicker
          aria-label="数据日期"
          value={dayjs(reportDate)}
          allowClear={false}
          onChange={(value) => {
            if (!value) return;
            setPreview(undefined);
            setGeneration(undefined);
            setReportDate(value.format('YYYY-MM-DD'));
          }}
        />
        <Space>
          <Button type="primary" className="admin-btn-query" onClick={() => { setPreview(undefined); setGeneration(undefined); reportsQuery.refetch(); detailQuery.refetch(); }}>
            查询
          </Button>
          {access.canRunDemoTasks && (
            <Button loading={previewMutation.isPending} onClick={() => previewMutation.mutate()}>
              预览
            </Button>
          )}
          {access.canRunDemoTasks && (
            <Button loading={generateMutation.isPending} onClick={() => generateMutation.mutate()}>
              生成日报
            </Button>
          )}
        </Space>
      </section>
      <section className={pageStyles.panel} aria-label="日报指标">
        <Space align="center" style={{ marginBottom: 16 }}>
          <Typography.Text type="secondary">报表状态</Typography.Text>
          <Tag color={reportStatusColor}>{reportStatus}</Tag>
        </Space>
        {preview?.status === 'READY' && (
          <Alert
            type="success"
            showIcon
            style={{ marginBottom: 16 }}
            message="临时报表已通过质量门禁"
            description={`数据范围：${previewRange}`}
          />
        )}
        {preview?.status === 'BLOCKED' && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="临时报表未通过质量门禁"
            description={(
              <Space direction="vertical" size={4}>
                <Typography.Text type="secondary">
                  数据范围：{previewRange}
                </Typography.Text>
                {failedQualityChecks.map((check) => (
                  <Typography.Text key={check.ruleCode}>
                    {`${QUALITY_CHECK_LABEL[check.ruleCode] ?? `未知质量检查（${check.ruleCode}）`}：失败 ${check.failedCount} 条，检查 ${check.checkedCount} 条`}
                  </Typography.Text>
                ))}
                {(preview.failures ?? []).slice(0, 20).map((failure) => (
                  <Typography.Text key={failure.eventId} type="secondary">
                    {`事件 ${failure.eventId}：${failure.reason}（已重试 ${failure.retryCount} 次，状态 ${failure.status}）`}
                  </Typography.Text>
                ))}
                {(preview.failures ?? []).length > 20 && (
                  <Typography.Text type="secondary">其余失败事件请在数据质量页面查看。</Typography.Text>
                )}
                <Button
                  type="link"
                  size="small"
                  style={{ paddingInline: 0 }}
                  onClick={() => history.push('/admin/data-quality', {
                    previewQuality: {
                      windowStart: preview.windowStart,
                      windowEnd: preview.windowEnd,
                      qualityChecks: preview.qualityChecks,
                    },
                  })}
                >
                  查看数据质量
                </Button>
              </Space>
            )}
          />
        )}
        {generation?.status === 'BLOCKED' && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="正式日报未通过质量门禁"
            description={(
              <Space direction="vertical" size={4}>
                <Typography.Text type="secondary">数据日期：{generation.reportDate}</Typography.Text>
                {failedGenerationChecks.map((check) => (
                  <Typography.Text key={check.ruleCode}>
                    {`${QUALITY_CHECK_LABEL[check.ruleCode] ?? '未知质量检查'}：失败 ${check.failedCount} 条，检查 ${check.checkedCount} 条`}
                  </Typography.Text>
                ))}
                {(generation.failures ?? []).slice(0, 20).map((failure) => (
                  <Typography.Text key={failure.eventId} type="secondary">
                    {`事件 ${failure.eventId}：${failure.reason}（已重试 ${failure.retryCount} 次，状态 ${failure.status}）`}
                  </Typography.Text>
                ))}
              </Space>
            )}
          />
        )}
        {detail && (
          <>
            <Descriptions className={styles.meta} size="small" column={{ xs: 1, sm: 2, lg: 4 }} bordered>
              <Descriptions.Item label="报表日期">{detail.reportMeta.reportDate}</Descriptions.Item>
              <Descriptions.Item label="生成时间">{new Date(detail.reportMeta.generatedAt).toLocaleString()}</Descriptions.Item>
              <Descriptions.Item label="报表版本">{detail.reportMeta.reportVersion}</Descriptions.Item>
              <Descriptions.Item label="数据来源">{detail.reportMeta.dataSources.map(dataSourceLabel).join('、')}</Descriptions.Item>
            </Descriptions>
            {overview && (
              <Row gutter={[12, 12]} className={styles.overview}>
                <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="交易笔数" value={overview.transactionCount} suffix="笔" /><Typography.Text type="secondary">较昨日 {formatBps(overview.dayOverDayChanges.transactionCountBps)}</Typography.Text></Card></Col>
                <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="交易金额" value={formatAmountFen(overview.transactionAmountFen)} prefix="¥" /><Typography.Text type="secondary">较昨日 {formatBps(overview.dayOverDayChanges.transactionAmountBps)}</Typography.Text></Card></Col>
                <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="支付成功率" value={(overview.successRateBps / 100).toFixed(2)} suffix="%" /><Typography.Text type="secondary">较昨日 {formatBps(overview.dayOverDayChanges.successRateBps)}</Typography.Text></Card></Col>
                <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="平均网关耗时" value={overview.averageLatencyMs} suffix="ms" /><Typography.Text type="secondary">较昨日 {formatBps(overview.dayOverDayChanges.averageLatencyBps)}</Typography.Text></Card></Col>
              </Row>
            )}
            <section className={styles.reportSection} aria-label="交易趋势">
              <Typography.Title level={5}>交易趋势</Typography.Title>
              <div className={styles.trendChart}>
                {detail.transactionTrend.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无趋势数据" /> : detail.transactionTrend.map((point) => (
                  <div className={styles.trendItem} key={point.timeBucket} title={`${dayjs(point.timeBucket).format('HH:00')}，${point.transactionCount} 笔，金额 ¥${formatAmountFen(point.amountFen)}`}>
                    <div className={`${styles.trendBar} ${styles.successBar}`} style={{ height: `${Math.max(point.transactionCount / trendMax * 100, 8)}%` }} />
                    <Typography.Text type="secondary">{dayjs(point.timeBucket).format('HH:00')}</Typography.Text>
                  </div>
                ))}
              </div>
            </section>
            <section className={styles.reportSection} aria-label="对账明细">
              <Typography.Title level={5}>对账明细</Typography.Title>
              <Table<DailyReportDetail['reconciliation'][number]>
                rowKey={(row) => `${row.transactionId}-${row.occurredAt}-${row.differenceType}`}
                size="small"
                pagination={false}
                dataSource={detail.reconciliation}
                columns={[
                  { title: '凭证号', dataIndex: 'voucherNo', render: (value: string | null) => value ?? '未携带' },
                  { title: '交易号', dataIndex: 'transactionId' },
                  { title: '时间', dataIndex: 'occurredAt', render: (value: string) => new Date(value).toLocaleTimeString() },
                  { title: '金额', dataIndex: 'amountFen', render: (value: number) => formatAmountFen(value) },
                  { title: '差异类型', dataIndex: 'differenceType', render: reconciliationTypeLabel },
                  { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{operatingStatusLabel(value)}</Tag> },
                ]}
                locale={{ emptyText: '暂无对账差异' }}
              />
            </section>
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <section className={styles.reportSection} aria-label="数据质量">
                  <Typography.Title level={5}>数据质量</Typography.Title>
                  <Table<DailyReportDetail['quality'][number]>
                    rowKey="dimension"
                    size="small"
                    pagination={false}
                    dataSource={detail.quality}
                    columns={[
                      { title: '维度', dataIndex: 'dimension', render: qualityRuleLabel },
                      { title: '定义', dataIndex: 'definition' },
                      { title: '当前值', dataIndex: 'currentValue', render: (value: number | null) => value ?? '-' },
                      { title: '阈值', dataIndex: 'threshold', render: (value: number | null) => value ?? '-' },
                      { title: '结论', dataIndex: 'conclusion', render: (value: string) => <Tag color={statusColor(value)}>{qualityConclusion(value)}</Tag> },
                    ]}
                    locale={{ emptyText: '暂无质量检查' }}
                  />
                </section>
              </Col>
              <Col xs={24} lg={12}>
                <section className={styles.reportSection} aria-label="告警汇总">
                  <Typography.Title level={5}>告警汇总</Typography.Title>
                  <Table<DailyReportDetail['alerts'][number]>
                    rowKey="alertId"
                    size="small"
                    pagination={false}
                    dataSource={detail.alerts}
                    columns={[
                      { title: '级别', dataIndex: 'level', render: alertLevelLabel },
                      { title: '内容', dataIndex: 'content' },
                      { title: '发生时间', dataIndex: 'occurredAt', render: (value: string) => new Date(value).toLocaleTimeString() },
                      { title: '处置', dataIndex: 'action' },
                      { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{operatingStatusLabel(value)}</Tag> },
                    ]}
                    locale={{ emptyText: '暂无告警' }}
                  />
                </section>
              </Col>
            </Row>
          </>
        )}
        <Typography.Title level={5} className={styles.metricTitle}>T+1 指标数据</Typography.Title>
        <Table<DailyMetricItem>
          rowKey="metricCode"
          columns={columns}
          dataSource={displayedReports}
          loading={reportsQuery.isLoading || definitionsQuery.isLoading || detailQuery.isLoading || definitionsQuery.isLoading || previewMutation.isPending || generateMutation.isPending}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                description={
                  preview?.status === 'BLOCKED'
                    ? '临时报表未通过质量门禁，请查看数据质量结果'
                    : (reportsQuery.error as GatewayRequestError | undefined)?.code === 'REPORT_NOT_PUBLISHED'
                    ? '该日期报表尚未发布'
                    : reportsQuery.isError
                      ? '加载失败，请确认网关已启动'
                      : '该日期报表尚未发布'
                }
              />
            ),
          }}
          scroll={{ x: 800 }}
        />
      </section>
    </main>
  );
}
