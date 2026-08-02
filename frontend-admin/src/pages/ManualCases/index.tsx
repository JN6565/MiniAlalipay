import { Button, DatePicker, Empty, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

interface ManualCaseRow {
  caseId: string;
  transactionId?: string;
  createdAt: string;
  amountFen: number;
  reason: string;
  status: string;
  operatorId?: string;
}

const columns: ColumnsType<ManualCaseRow> = [
  { title: '工单号', dataIndex: 'caseId', key: 'caseId' },
  { title: '交易号', dataIndex: 'transactionId', key: 'transactionId' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '金额', dataIndex: 'amountFen', key: 'amountFen' },
  { title: '风险或故障原因', dataIndex: 'reason', key: 'reason' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '负责人', dataIndex: 'operatorId', key: 'operatorId' },
  { title: '操作', key: 'actions', render: () => <Button disabled>查看详情</Button> },
];

export default function ManualCases() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="人工确认台"
        description="查看脱敏异常上下文并处理风险预检或事务恢复工单。"
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="工单筛选">
        <Select
          aria-label="工单状态"
          allowClear
          placeholder="工单状态"
          style={{ width: 180 }}
          options={[
            { value: 'OPEN', label: '待处理' },
            { value: 'ACKNOWLEDGED', label: '已确认' },
            { value: 'RESOLVED', label: '已解决' },
            { value: 'CLOSED', label: '已关闭' },
          ]}
        />
        <Select
          aria-label="工单类型"
          allowClear
          placeholder="工单类型"
          style={{ width: 220 }}
          options={[
            { value: 'RISK_PRECHECK', label: '风险预检' },
            { value: 'TRANSACTION_RECOVERY', label: '事务恢复' },
          ]}
        />
        <DatePicker.RangePicker aria-label="创建时间范围" />
        <Button type="primary" disabled>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="工单列表">
        <Table<ManualCaseRow>
          rowKey="caseId"
          columns={columns}
          dataSource={[]}
          pagination={false}
          locale={{ emptyText: <Empty description="工单接口接入后展示脱敏工单" /> }}
          scroll={{ x: 960 }}
        />
      </section>
    </main>
  );
}
