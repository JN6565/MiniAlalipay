import { CalendarOutlined, FileDoneOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { App, Button, Card, DatePicker, Row, Col, Space, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { runCreditDueCheck, runCreditStatement, type CreditJobRun } from '@/services/ops';
import pageStyles from '../page.less';

/** 信用运维任务运行状态中文标签与颜色。 */
const JOB_STATUS_META: Record<string, { text: string; color: string }> = {
  PENDING: { text: '排队中', color: 'default' },
  RUNNING: { text: '执行中', color: 'processing' },
  SUCCESS: { text: '成功', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
  MANUAL_REVIEW: { text: '待人工复核', color: 'warning' },
};

/** 最近一次任务运行结果摘要。 */
function JobResult({ title, result }: { title: string; result?: CreditJobRun }) {
  if (!result) return null;
  const meta = JOB_STATUS_META[result.status] ?? { text: result.status, color: 'default' };
  return (
    <Space direction="vertical" size={4} data-testid={`${title}-result`}>
      <Space size={8}>
        <Typography.Text type="secondary">运行号</Typography.Text>
        <Typography.Text code>{result.runId}</Typography.Text>
        <Tag color={meta.color}>{meta.text}</Tag>
      </Space>
      <Space size={8} wrap>
        {result.startedAt && (
          <>
            <Typography.Text type="secondary">开始</Typography.Text>
            <Typography.Text>{new Date(result.startedAt).toLocaleString()}</Typography.Text>
          </>
        )}
        {result.completedAt && (
          <>
            <Typography.Text type="secondary">完成</Typography.Text>
            <Typography.Text>{new Date(result.completedAt).toLocaleString()}</Typography.Text>
          </>
        )}
        {result.errorCode && (
          <>
            <Typography.Text type="secondary">错误码</Typography.Text>
            <Typography.Text type="danger">{result.errorCode}</Typography.Text>
          </>
        )}
      </Space>
    </Space>
  );
}

/**
 * 演示任务触发页。
 *
 * 由系统管理员受审计地触发信用出账或到期检查；业务日期同时是服务端幂等维度，
 * 同一业务日期重复执行由服务端幂等返回原结果。触发动作携带服务端幂等键，并展示任务运行状态。
 */
export default function DemoTasks() {
  const { message } = App.useApp();
  const [businessDate, setBusinessDate] = useState<string>(dayjs().format('YYYY-MM-DD'));
  const [statementResult, setStatementResult] = useState<CreditJobRun>();
  const [dueCheckResult, setDueCheckResult] = useState<CreditJobRun>();

  const statementMutation = useMutation({
    mutationFn: () => runCreditStatement(businessDate),
    onSuccess: (result) => {
      setStatementResult(result.data);
      message.success('信用出账任务已触发');
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '触发出账失败'),
  });

  const dueCheckMutation = useMutation({
    mutationFn: () => runCreditDueCheck(businessDate),
    onSuccess: (result) => {
      setDueCheckResult(result.data);
      message.success('到期检查任务已触发');
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '触发到期检查失败'),
  });

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.panel} aria-label="演示任务表单">
        <Space direction="vertical" size={8}>
          <Typography.Text strong>业务日期</Typography.Text>
          <DatePicker
            aria-label="业务日期"
            value={dayjs(businessDate)}
            allowClear={false}
            onChange={(value) => value && setBusinessDate(value.format('YYYY-MM-DD'))}
          />
        </Space>
      </section>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="信用出账任务" extra={<FileDoneOutlined />}>
            <Typography.Paragraph type="secondary">
              同一业务日期重复执行时由服务端幂等返回原执行结果。
            </Typography.Paragraph>
            <Space direction="vertical" size={12}>
              <Button
                type="primary"
                icon={<FileDoneOutlined />}
                loading={statementMutation.isPending}
                onClick={() => statementMutation.mutate()}
              >
                触发出账
              </Button>
              <JobResult title="出账" result={statementResult} />
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="到期检查任务" extra={<CalendarOutlined />}>
            <Typography.Paragraph type="secondary">
              仅推进契约允许的状态，不修改账单金额或绕过资金应用入口。
            </Typography.Paragraph>
            <Space direction="vertical" size={12}>
              <Button
                type="primary"
                icon={<CalendarOutlined />}
                loading={dueCheckMutation.isPending}
                onClick={() => dueCheckMutation.mutate()}
              >
                触发到期检查
              </Button>
              <JobResult title="到期检查" result={dueCheckResult} />
            </Space>
          </Card>
        </Col>
      </Row>
    </main>
  );
}
