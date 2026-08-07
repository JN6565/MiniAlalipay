import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Empty, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { listDataQuality, type DataQualityItem } from '@/services/ops';
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

/**
 * 数据质量页面。
 *
 * 交互约定：先按数据日期查询该日期全部质量结果，从结果中提取任务编码与规则编码，生成
 * 可选、可搜索的级联下拉，运营人员不必猜测编码。默认“全部”不填也可查询；选中任务后
 * 规则下拉只展示该任务对应的规则，再按精确条件过滤。结果表保留任务/规则编码列，
 * 便于从查询结果直接复用筛选条件。
 */
export default function DataQuality() {
  const [dataDate, setDataDate] = useState<string>(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
  const [jobCode, setJobCode] = useState<string>();
  const [ruleCode, setRuleCode] = useState<string>();

  // 该日期全部结果：作为下拉选项的数据源，用于提取任务/规则编码并做级联过滤。
  const optionsQuery = useQuery({
    queryKey: ['ops', 'data-quality-options', dataDate],
    queryFn: () => listDataQuality(dataDate),
  });
  // 按当前筛选条件查询结果表；jobCode/ruleCode 为空即“全部”，同样可查询。
  const qualityQuery = useQuery({
    queryKey: ['ops', 'data-quality', dataDate, jobCode, ruleCode],
    queryFn: () => listDataQuality(dataDate, jobCode, ruleCode),
  });

  const allResults = optionsQuery.data?.data ?? [];

  /** 任务下拉选项：取该日期全部结果中出现的任务编码去重；值为编码本身，展示业务名（如“交易完整性”）。 */
  const taskOptions = useMemo(() => {
    const tasks = [...new Set(allResults.map((item) => item.taskCode))].sort();
    return [{ label: '全部', value: '' }, ...tasks.map((task) => ({ label: task, value: task }))];
  }, [allResults]);

  /** 规则下拉选项：选中任务后只展示该任务对应的规则；未选中则展示全部规则。 */
  const ruleOptions = useMemo(() => {
    const filtered = jobCode ? allResults.filter((item) => item.taskCode === jobCode) : allResults;
    const rules = [...new Set(filtered.map((item) => item.ruleCode))].sort();
    return [{ label: '全部', value: '' }, ...rules.map((rule) => ({ label: rule, value: rule }))];
  }, [allResults, jobCode]);

  /** 切换数据日期时重置级联筛选，重新拉取该日期全部结果。 */
  const handleDateChange = (date: dayjs.Dayjs | null) => {
    if (!date) return;
    setDataDate(date.format('YYYY-MM-DD'));
    setJobCode(undefined);
    setRuleCode(undefined);
  };

  const columns: ColumnsType<DataQualityItem> = [
    { title: '检查结果号', dataIndex: 'resultId' },
    { title: '任务编码', dataIndex: 'taskCode' },
    { title: '规则编码', dataIndex: 'ruleCode' },
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

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="质量结果筛选">
        <DatePicker aria-label="数据日期" value={dayjs(dataDate)} allowClear={false} onChange={handleDateChange} />
        <Select
          aria-label="任务"
          placeholder="全部任务"
          value={jobCode ?? ''}
          options={taskOptions}
          showSearch
          optionFilterProp="label"
          loading={optionsQuery.isFetching}
          style={{ width: 220 }}
          onChange={(value: string) => {
            setJobCode(value || undefined);
            // 任务变化后规则选项随之变化，规则重置为“全部”。
            setRuleCode(undefined);
          }}
        />
        <Select
          aria-label="规则"
          placeholder="全部规则"
          value={ruleCode ?? ''}
          options={ruleOptions}
          showSearch
          optionFilterProp="label"
          loading={optionsQuery.isFetching}
          style={{ width: 220 }}
          onChange={(value: string) => setRuleCode(value || undefined)}
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
          scroll={{ x: 860 }}
        />
      </section>
    </main>
  );
}
