import { Button, Empty, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

interface AlertRow {
  alertId: string;
  severity: string;
  status: string;
  ruleCode: string;
  subjectId: string;
  assigneeId?: string;
  openedAt: string;
  updatedAt: string;
}

const columns: ColumnsType<AlertRow> = [
  { title: '告警号', dataIndex: 'alertId', key: 'alertId' },
  { title: '级别', dataIndex: 'severity', key: 'severity' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '规则', dataIndex: 'ruleCode', key: 'ruleCode' },
  { title: '关联对象', dataIndex: 'subjectId', key: 'subjectId' },
  { title: '负责人', dataIndex: 'assigneeId', key: 'assigneeId' },
  { title: '首次发现', dataIndex: 'openedAt', key: 'openedAt' },
  { title: '最近更新', dataIndex: 'updatedAt', key: 'updatedAt' },
  { title: '操作', key: 'actions', render: () => <Button disabled>处置</Button> },
];

export default function Alerts() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="告警中心"
        description="按级别和状态跟踪资金一致性、事务健康及数据任务告警。"
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="告警筛选">
        <Select
          aria-label="告警级别"
          allowClear
          placeholder="告警级别"
          style={{ width: 160 }}
          options={[
            { value: 'P0', label: 'P0' },
            { value: 'P1', label: 'P1' },
            { value: 'P2', label: 'P2' },
          ]}
        />
        <Select
          aria-label="告警状态"
          allowClear
          placeholder="告警状态"
          style={{ width: 180 }}
          options={[
            { value: 'OPEN', label: '待确认' },
            { value: 'ACKNOWLEDGED', label: '已确认' },
            { value: 'RESOLVED', label: '已恢复' },
            { value: 'CLOSED', label: '已关闭' },
          ]}
        />
        <Button type="primary" disabled>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="告警列表">
        <Table<AlertRow>
          rowKey="alertId"
          columns={columns}
          dataSource={[]}
          pagination={false}
          locale={{ emptyText: <Empty description="告警接口接入后展示告警时间线" /> }}
          scroll={{ x: 1080 }}
        />
      </section>
    </main>
  );
}
