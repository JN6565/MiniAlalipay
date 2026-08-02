import { CalendarOutlined, FileDoneOutlined } from '@ant-design/icons';
import { Button, Card, DatePicker, Row, Col, Space, Typography } from 'antd';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

export default function DemoTasks() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="演示任务触发"
        description="由演示管理员受审计地触发出账或到期检查，不直接修改账单金额。"
        contractPending
      />
      <section className={pageStyles.panel} aria-label="演示任务表单">
        <Space direction="vertical" size={8}>
          <Typography.Text strong>业务日期</Typography.Text>
          <DatePicker aria-label="业务日期" />
        </Space>
      </section>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="信用出账任务" extra={<FileDoneOutlined />}>
            <Typography.Paragraph type="secondary">
              同一业务日期重复执行时应由服务端幂等返回原执行结果。
            </Typography.Paragraph>
            <Button type="primary" icon={<FileDoneOutlined />} disabled>
              触发出账
            </Button>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="到期检查任务" extra={<CalendarOutlined />}>
            <Typography.Paragraph type="secondary">
              仅推进契约允许的状态，不修改账单金额或绕过资金应用入口。
            </Typography.Paragraph>
            <Button type="primary" icon={<CalendarOutlined />} disabled>
              触发到期检查
            </Button>
          </Card>
        </Col>
      </Row>
    </main>
  );
}
