import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Empty, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { GatewayRequestError } from '@/services/request';
import { listDailyReports, listMetricDefinitions, type DailyMetricItem } from '@/services/ops';
import pageStyles from '../page.less';

/**
 * T+1 报表页面。
 *
 * 读取通过质量门禁的真实日报指标，并按指标口径定义展示中文名称与单位，只读展示；
 * 指标值为整数，不做前端金额换算。
 */
export default function Reports() {
  const [reportDate, setReportDate] = useState<string>(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));

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

  const columns: ColumnsType<DailyMetricItem> = [
    { title: '指标编码', dataIndex: 'metricCode' },
    {
      title: '指标名称',
      key: 'metricName',
      render: (_, record) => definitionByCode.get(record.metricCode)?.name ?? record.metricCode,
    },
    { title: '数据日期', dataIndex: 'reportDate' },
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
      render: (value: string) => <Tag color={value === 'PASSED' ? 'green' : 'orange'}>{value}</Tag>,
    },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader extra={<Tag>只读</Tag>} />
      <section className={pageStyles.toolbar} aria-label="报表筛选">
        <Typography.Text>数据日期</Typography.Text>
        <DatePicker
          aria-label="数据日期"
          value={dayjs(reportDate)}
          allowClear={false}
          onChange={(value) => value && setReportDate(value.format('YYYY-MM-DD'))}
        />
        <Button type="primary" onClick={() => reportsQuery.refetch()}>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="日报指标">
        <Table<DailyMetricItem>
          rowKey="metricCode"
          columns={columns}
          dataSource={reportsQuery.data?.data ?? []}
          loading={reportsQuery.isLoading || definitionsQuery.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                description={
                  (reportsQuery.error as GatewayRequestError | undefined)?.code === 'REPORT_NOT_PUBLISHED'
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
