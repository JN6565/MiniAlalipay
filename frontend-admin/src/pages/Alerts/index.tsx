import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Empty, Form, Input, Modal, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { acknowledgeAlert, closeAlert, listAlerts, resolveAlert, type AlertItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 告警处置动作类型。 */
type AlertAction = 'acknowledge' | 'resolve' | 'close';

/** 告警状态中文标签。 */
const STATUS_LABEL: Record<string, string> = {
  OPEN: '待确认',
  ACKNOWLEDGED: '已确认',
  RESOLVED: '已恢复',
  CLOSED: '已关闭',
};

/**
 * 告警中心。
 *
 * 读取真实监控投影，支持确认/恢复/关闭处置；处置携带服务端幂等键与 CAS 版本，
 * 操作人由网关身份注入，展示仅含脱敏业务字段。
 */
export default function Alerts() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<string>();
  const [action, setAction] = useState<{ alert: AlertItem; type: AlertAction }>();
  const [form] = Form.useForm();

  const alertsQuery = useQuery({
    queryKey: ['ops', 'alerts', status],
    queryFn: () => listAlerts(status),
  });

  const mutation = useMutation({
    mutationFn: () => {
      const target = action;
      if (!target) throw new Error('缺少处置对象');
      const values = form.getFieldsValue();
      if (target.type === 'acknowledge') {
        return acknowledgeAlert(target.alert.alertId, target.alert.version, values.reason);
      }
      if (target.type === 'resolve') {
        return resolveAlert(target.alert.alertId, target.alert.version, values.reason, values.evidence);
      }
      return closeAlert(target.alert.alertId, target.alert.version, values.reason, values.evidence);
    },
    onSuccess: () => {
      message.success('告警处置成功');
      setAction(undefined);
      queryClient.invalidateQueries({ queryKey: ['ops', 'alerts'] });
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '告警处置失败');
    },
  });

  const columns: ColumnsType<AlertItem> = [
    { title: '告警号', dataIndex: 'alertId' },
    { title: '规则', dataIndex: 'alertType' },
    { title: '级别', dataIndex: 'severity' },
    { title: '状态', dataIndex: 'status', render: (value: string) => STATUS_LABEL[value] ?? value },
    { title: '创建时间', dataIndex: 'createdAt' },
    { title: '最近更新', dataIndex: 'updatedAt' },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        if (record.status === 'OPEN') {
          return <Button size="small" onClick={() => setAction({ alert: record, type: 'acknowledge' })}>确认</Button>;
        }
        if (record.status === 'ACKNOWLEDGED') {
          return <Button size="small" onClick={() => setAction({ alert: record, type: 'resolve' })}>恢复</Button>;
        }
        if (record.status === 'RESOLVED') {
          return <Button size="small" onClick={() => setAction({ alert: record, type: 'close' })}>关闭</Button>;
        }
        return null;
      },
    },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="告警筛选">
        <Select
          aria-label="告警状态"
          allowClear
          placeholder="告警状态"
          style={{ width: 180 }}
          options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          onChange={setStatus}
        />
        <Button type="primary" onClick={() => alertsQuery.refetch()}>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="告警列表">
        <Table<AlertItem>
          rowKey="alertId"
          columns={columns}
          dataSource={alertsQuery.data?.data.items ?? []}
          loading={alertsQuery.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description={alertsQuery.isError ? '加载失败，请确认网关已启动' : '暂无告警'} />
            ),
          }}
          scroll={{ x: 980 }}
        />
      </section>
      <Modal
        title={action ? `处置告警（${STATUS_LABEL[action.alert.status] ?? action.alert.status}）` : ''}
        open={!!action}
        confirmLoading={mutation.isPending}
        onOk={() => mutation.mutate()}
        onCancel={() => setAction(undefined)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ reason: '', evidence: '' }}>
          <Form.Item name="reason" label="处置理由" rules={[{ required: true, message: '请输入处置理由' }]}>
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          {action?.type !== 'acknowledge' && (
            <Form.Item name="evidence" label="处置证据" rules={[{ required: true, message: '请输入处置证据' }]}>
              <Input.TextArea rows={3} maxLength={2000} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </main>
  );
}
