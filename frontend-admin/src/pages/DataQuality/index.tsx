import { DatePicker, Empty, Input, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

interface QualityResultRow {
  resultId: string;
  taskCode: string;
  ruleCode: string;
  dataDate: string;
  status: string;
  checkedAt: string;
}

const columns: ColumnsType<QualityResultRow> = [
  { title: '检查结果号', dataIndex: 'resultId', key: 'resultId' },
  { title: '任务', dataIndex: 'taskCode', key: 'taskCode' },
  { title: '规则', dataIndex: 'ruleCode', key: 'ruleCode' },
  { title: '数据日期', dataIndex: 'dataDate', key: 'dataDate' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '检查时间', dataIndex: 'checkedAt', key: 'checkedAt' },
];

export default function DataQuality() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="数据质量"
        description="核查事件完整性、唯一性、合法性、及时性及报表发布门禁。"
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="质量结果筛选">
        <DatePicker aria-label="数据日期" placeholder="数据日期" />
        <Input aria-label="任务编码" placeholder="任务编码" style={{ width: 200 }} />
        <Input aria-label="规则编码" placeholder="规则编码" style={{ width: 200 }} />
        <Select
          aria-label="质量状态"
          allowClear
          placeholder="质量状态"
          style={{ width: 160 }}
          options={[
            { value: 'PASSED', label: '通过' },
            { value: 'WARNING', label: '警告' },
            { value: 'FAILED', label: '失败' },
          ]}
        />
      </section>
      <section className={pageStyles.panel} aria-label="质量检查结果">
        <Table<QualityResultRow>
          rowKey="resultId"
          columns={columns}
          dataSource={[]}
          pagination={false}
          locale={{ emptyText: <Empty description="质量接口接入后展示检查结果和隔离摘要" /> }}
          scroll={{ x: 760 }}
        />
      </section>
    </main>
  );
}
