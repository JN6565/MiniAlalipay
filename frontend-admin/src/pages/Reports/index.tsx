import { DatePicker, Empty, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

interface DailyMetricRow {
  metricCode: string;
  metricName: string;
  metricValue: string;
  dataDate: string;
  qualityStatus: string;
  calibrationVersion: string;
}

const columns: ColumnsType<DailyMetricRow> = [
  { title: '指标名称', dataIndex: 'metricName', key: 'metricName' },
  { title: '指标编码', dataIndex: 'metricCode', key: 'metricCode' },
  { title: '指标值', dataIndex: 'metricValue', key: 'metricValue' },
  { title: '数据日期', dataIndex: 'dataDate', key: 'dataDate' },
  { title: '质量状态', dataIndex: 'qualityStatus', key: 'qualityStatus' },
  { title: '口径版本', dataIndex: 'calibrationVersion', key: 'calibrationVersion' },
];

export default function Reports() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="T+1 报表"
        description="查看上一自然日的业务指标、生成时间、质量状态和指标口径。"
        extra={<Tag>只读</Tag>}
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="报表筛选">
        <Typography.Text>数据日期</Typography.Text>
        <DatePicker aria-label="数据日期" />
      </section>
      <section className={pageStyles.panel} aria-label="日报指标">
        <Table<DailyMetricRow>
          rowKey="metricCode"
          columns={columns}
          dataSource={[]}
          pagination={false}
          locale={{ emptyText: <Empty description="日报接口接入后展示最近七个数据日" /> }}
          scroll={{ x: 800 }}
        />
      </section>
    </main>
  );
}
