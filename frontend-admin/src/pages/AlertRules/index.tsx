import { useAccess } from '@umijs/max';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Form, InputNumber, Modal, Space, Table, Tag, Typography, App } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { listAlertRules, updateAlertRuleThreshold, type AlertRuleItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 严重级别展示标签。 */
const severityLabels: Record<string, { text: string; color: string }> = {
  CRITICAL: { text: '严重', color: 'red' },
  WARNING: { text: '警告', color: 'orange' },
  INFO: { text: '提示', color: 'blue' },
};

/** 比较算子展示文本。 */
const operatorLabels: Record<string, string> = {
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于',
};

/**
 * 告警规则与阈值配置页面。
 *
 * 展示监控告警触发规则（指标代码、比较算子、阈值、严重级别），管理员可在版本 CAS 下调整阈值。
 * 页面只操作运营投影，不修改交易、账户或账本事实；写操作携带服务端幂等键。
 */
export default function AlertRules() {
  const { message } = App.useApp();
  const access = useAccess();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<AlertRuleItem | null>(null);
  const [form] = Form.useForm<{ thresholdValue: number }>();

  const { data, isFetching } = useQuery({
    queryKey: ['ops-alert-rules'],
    queryFn: () => listAlertRules(),
  });

  const rows: AlertRuleItem[] = data?.data ?? [];

  const mutation = useMutation({
    mutationFn: (values: { ruleCode: string; thresholdValue: number; version: number }) =>
      updateAlertRuleThreshold(values.ruleCode, values.thresholdValue, values.version),
    onSuccess: (result) => {
      message.success(`规则 ${result.data.ruleName} 阈值已更新`);
      setEditing(null);
      queryClient.invalidateQueries({ queryKey: ['ops-alert-rules'] });
    },
    onError: (error: Error) => {
      message.error(`更新失败：${error.message}`);
    },
  });

  const openEdit = (rule: AlertRuleItem) => {
    setEditing(rule);
    form.setFieldsValue({ thresholdValue: rule.thresholdValue });
  };

  const submit = async () => {
    if (!editing) return;
    const values = await form.validateFields();
    mutation.mutate({ ruleCode: editing.ruleCode, thresholdValue: values.thresholdValue, version: editing.version });
  };

  const columns: TableProps<AlertRuleItem>['columns'] = [
    { title: '规则代码', dataIndex: 'ruleCode', width: 200, ellipsis: true },
    { title: '规则名称', dataIndex: 'ruleName', width: 180 },
    { title: '指标代码', dataIndex: 'metricCode', width: 220, ellipsis: true },
    {
      title: '严重级别',
      dataIndex: 'severity',
      width: 100,
      render: (value: string) => {
        const label = severityLabels[value] ?? { text: value, color: 'default' };
        return <Tag color={label.color}>{label.text}</Tag>;
      },
    },
    {
      title: '触发条件',
      key: 'condition',
      width: 140,
      render: (_, record) => `${operatorLabels[record.operator] ?? record.operator} ${record.thresholdValue}`,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (value: boolean) => (value ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '最近更新', dataIndex: 'updatedAt', width: 180, render: (value: string) => new Date(value).toLocaleString() },
    { title: '操作者', dataIndex: 'updatedBy', width: 140 },
    ...(access.canConfigureAlertThresholds
      ? [{
          title: '操作' as const,
          key: 'action' as const,
          width: 120,
          render: (_: unknown, record: AlertRuleItem) => (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              调整阈值
            </Button>
          ),
        }]
      : []),
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.panel} aria-label="告警规则列表">
        <Typography.Paragraph type="secondary">
          告警规则为运营投影，不持有资金事实；调整阈值只影响后续告警判断，不追溯历史告警。
        </Typography.Paragraph>
        <Table<AlertRuleItem>
          rowKey="ruleCode"
          size="middle"
          loading={isFetching}
          columns={columns}
          dataSource={rows}
          pagination={false}
        />
      </section>
      <Modal
        title={editing ? `调整阈值：${editing.ruleName}` : '调整阈值'}
        open={!!editing}
        onOk={submit}
        confirmLoading={mutation.isPending}
        onCancel={() => setEditing(null)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="触发阈值"
            name="thresholdValue"
            rules={[{ required: true, message: '请输入触发阈值' }]}
            extra="当指标值满足比较算子与阈值时触发告警；阈值为非负整数。"
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
        <Space size="small" wrap>
          <Typography.Text type="secondary">规则代码：{editing?.ruleCode}</Typography.Text>
          <Typography.Text type="secondary">指标：{editing?.metricCode}</Typography.Text>
          <Typography.Text type="secondary">比较算子：{editing ? (operatorLabels[editing.operator] ?? editing.operator) : ''}</Typography.Text>
        </Space>
      </Modal>
    </main>
  );
}
