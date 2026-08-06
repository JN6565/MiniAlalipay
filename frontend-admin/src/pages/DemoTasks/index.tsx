import { CalendarOutlined, FileDoneOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { App, Button, Card, DatePicker, Row, Col, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { runCreditDueCheck, runCreditStatement } from '@/services/ops';
import pageStyles from '../page.less';

/**
 * 演示任务触发页。
 *
 * 由管理员受审计地触发信用出账或到期检查；业务日期同时是服务端幂等维度，
 * 同一业务日期重复执行由服务端幂等返回原结果。触发动作携带服务端幂等键。
 */
export default function DemoTasks() {
  const { message } = App.useApp();
  const [businessDate, setBusinessDate] = useState<string>(dayjs().format('YYYY-MM-DD'));

  const statementMutation = useMutation({
    mutationFn: () => runCreditStatement(businessDate),
    onSuccess: () => message.success('信用出账任务已触发'),
    onError: (error) => message.error(error instanceof Error ? error.message : '触发出账失败'),
  });

  const dueCheckMutation = useMutation({
    mutationFn: () => runCreditDueCheck(businessDate),
    onSuccess: () => message.success('到期检查任务已触发'),
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
            <Button
              type="primary"
              icon={<FileDoneOutlined />}
              loading={statementMutation.isPending}
              onClick={() => statementMutation.mutate()}
            >
              触发出账
            </Button>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="到期检查任务" extra={<CalendarOutlined />}>
            <Typography.Paragraph type="secondary">
              仅推进契约允许的状态，不修改账单金额或绕过资金应用入口。
            </Typography.Paragraph>
            <Button
              type="primary"
              icon={<CalendarOutlined />}
              loading={dueCheckMutation.isPending}
              onClick={() => dueCheckMutation.mutate()}
            >
              触发到期检查
            </Button>
          </Card>
        </Col>
      </Row>
    </main>
  );
}
