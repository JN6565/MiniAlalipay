import { useLocation } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Empty, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { listDataQuality, type DailyReportPreview, type DataQualityItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 质量状态中文标签。 */
const STATUS_LABEL: Record<string, string> = {
  PASSED: '通过',
  WARNING: '警告',
  FAILED: '失败',
  UNKNOWN: '未知',
};

/** 质量状态标签配色。 */
const STATUS_COLOR: Record<string, string> = {
  PASSED: 'success',
  WARNING: 'warning',
  FAILED: 'error',
  UNKNOWN: 'default',
};

/** 临时报表返回的只读质量检查状态，仅用于页面间展示，不写入正式质量投影。 */
type PreviewQuality = Pick<DailyReportPreview, 'windowStart' | 'windowEnd' | 'qualityChecks'>;

/**
 * 数据质量页面。
 *
 * 读取真实质量检查投影，按数据日期筛选；展示检查数量与
 * 失败数量，不展示敏感字段。
 */
export default function DataQuality() {
  const location = useLocation();
  const previewQuality = (location.state as { previewQuality?: PreviewQuality } | null)?.previewQuality;
  const [dataDate, setDataDate] = useState<string>(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
  // 查询由网关透传至服务端，避免前端以局部结果替代真实查询结果。
  const qualityQuery = useQuery({
    queryKey: ['ops', 'data-quality', dataDate],
    queryFn: () => listDataQuality(dataDate, undefined, undefined),
  });

  /** 切换数据日期时清空编码条件，避免将上一日期的编码带入新查询。 */
  const handleDateChange = (date: dayjs.Dayjs | null) => {
    if (!date) return;
    setDataDate(date.format('YYYY-MM-DD'));
  };

  const columns: ColumnsType<DataQualityItem> = [
    { title: '检查结果号', dataIndex: 'resultId' },
    { title: '检查数量', dataIndex: 'checkedCount' },
    { title: '失败数量', dataIndex: 'failedCount' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '检查时间',
      dataIndex: 'completedAt',
      render: (value: string) => new Date(value).toLocaleString(),
    },
  ];
  const previewColumns: ColumnsType<PreviewQuality['qualityChecks'][number]> = [
    { title: '检查数量', dataIndex: 'checkedCount' },
    { title: '失败数量', dataIndex: 'failedCount' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>,
    },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="质量结果筛选">
        <DatePicker aria-label="数据日期" value={dayjs(dataDate)} allowClear={false} onChange={handleDateChange} />
        <Button type="primary" className="admin-btn-query" onClick={() => qualityQuery.refetch()} loading={qualityQuery.isFetching}>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="质量检查结果">
        {previewQuality && (
          <>
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 16 }}
              message="临时预览检查结果"
              description={(
                <Typography.Text type="secondary">
                  数据范围：{dayjs(previewQuality.windowStart).format('YYYY-MM-DD HH:mm:ss')} 至 {dayjs(previewQuality.windowEnd).format('YYYY-MM-DD HH:mm:ss')}。该结果仅对应刚才生成的临时报表，未写入正式质量结果。
                </Typography.Text>
              )}
            />
            <Table<PreviewQuality['qualityChecks'][number]>
              rowKey="ruleCode"
              columns={previewColumns}
              dataSource={previewQuality.qualityChecks}
              pagination={false}
              size="small"
              style={{ marginBottom: 20 }}
            />
          </>
        )}
        {previewQuality && <Typography.Title level={5}>正式质量结果</Typography.Title>}
        <Table<DataQualityItem>
          rowKey="resultId"
          columns={columns}
          dataSource={qualityQuery.data?.data ?? []}
          loading={qualityQuery.isFetching}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description={qualityQuery.isError ? '加载失败，请确认网关已启动' : '暂无符合条件的质量结果'} />
            ),
          }}
          scroll={{ x: 860 }}
        />
      </section>
    </main>
  );
}
