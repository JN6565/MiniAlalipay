import { history, useAccess } from '@umijs/max';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Alert, Button, DatePicker, Empty, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { GatewayRequestError } from '@/services/request';
import { generateDailyReport, generateDailyReportPreview, listDailyReports, listMetricDefinitions, type DailyMetricItem, type DailyReportGeneration, type DailyReportPreview } from '@/services/ops';
import pageStyles from '../page.less';

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
    : reportsQuery.data?.data ?? [];
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
          <Button type="primary" className="admin-btn-query" onClick={() => { setPreview(undefined); setGeneration(undefined); reportsQuery.refetch(); }}>
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
        <Table<DailyMetricItem>
          rowKey="metricCode"
          columns={columns}
          dataSource={displayedReports}
          loading={reportsQuery.isLoading || definitionsQuery.isLoading || previewMutation.isPending || generateMutation.isPending}
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
