import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Empty, Input, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { listDataQuality, type DataQualityItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 质量状态中文标签。 */
const STATUS_LABEL: Record<string, string> = {
  PASSED: '通过',
  WARNING: '警告',
  FAILED: '失败',
};

/**
 * 数据质量页面。
 *
 * 读取真实质量检查投影，按数据日期、任务编码与规则编码筛选；展示检查数量与
 * 失败数量，不展示敏感字段。
 */
export default function DataQuality() {
  const [dataDate, setDataDate] = useState<string>(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
  const [jobCode, setJobCode] = useState<string>();
  const [ruleCode, setRuleCode] = useState<string>();

  const qualityQuery = useQuery({
    queryKey: ['ops', 'data-quality', dataDate, jobCode, ruleCode],
    queryFn: () => listDataQuality(dataDate, jobCode, ruleCode),
  });

  const columns: ColumnsType<DataQualityItem> = [
    { title: '检查结果号', dataIndex: 'resultId' },
    { title: '检查类型', dataIndex: 'checkType' },
    { title: '检查数量', dataIndex: 'checkedCount' },
    { title: '失败数量', dataIndex: 'failedCount' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => STATUS_LABEL[value] ?? value,
    },
    { title: '检查时间', dataIndex: 'completedAt' },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="质量结果筛选">
        <DatePicker
          aria-label="数据日期"
          value={dayjs(dataDate)}
          allowClear={false}
          onChange={(value) => value && setDataDate(value.format('YYYY-MM-DD'))}
        />
        <Input
          aria-label="任务编码"
          placeholder="任务编码"
          allowClear
          style={{ width: 180 }}
          onChange={(event) => setJobCode(event.target.value.trim() || undefined)}
        />
        <Input
          aria-label="规则编码"
          placeholder="规则编码"
          allowClear
          style={{ width: 180 }}
          onChange={(event) => setRuleCode(event.target.value.trim() || undefined)}
        />
        <Button type="primary" onClick={() => qualityQuery.refetch()}>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="质量检查结果">
        <Table<DataQualityItem>
          rowKey="resultId"
          columns={columns}
          dataSource={qualityQuery.data?.data ?? []}
          loading={qualityQuery.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description={qualityQuery.isError ? '加载失败，请确认网关已启动' : '暂无符合条件的质量结果'} />
            ),
          }}
          scroll={{ x: 760 }}
        />
      </section>
    </main>
  );
}
