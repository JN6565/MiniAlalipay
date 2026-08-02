import {
  AlertOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Alert, Button, Card, Col, Row, Skeleton, Space, Statistic, Tag, Typography } from 'antd';
import { getGatewayHealth } from '@/services/health';
import styles from './index.less';

export default function Dashboard() {
  const healthQuery = useQuery({
    queryKey: ['gateway', 'health'],
    queryFn: getGatewayHealth,
    retry: 1,
    refetchInterval: 60_000,
  });

  const gatewayUp = healthQuery.data?.status === 'UP';

  return (
    <div className={styles.page}>
      <div className={styles.heading}>
        <div>
          <Typography.Title level={2}>可信运行看板</Typography.Title>
          <Typography.Text type="secondary">
            当前仅展示已由正式契约定义的网关健康状态。
          </Typography.Text>
        </div>
        <Tag color={gatewayUp ? 'success' : healthQuery.isLoading ? 'processing' : 'default'}>
          {gatewayUp ? '网关运行中' : healthQuery.isLoading ? '检查中' : '状态未知'}
        </Tag>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            {healthQuery.isLoading ? (
              <Skeleton active paragraph={{ rows: 1 }} title={false} />
            ) : (
              <div className={styles.metricBody}>
                <div className={`${styles.metricIcon} ${styles.metricIconBlue}`}>
                  {gatewayUp ? <CheckCircleOutlined /> : <ApiOutlined />}
                </div>
                <Statistic
                  title="网关状态"
                  value={gatewayUp ? '正常' : '不可用'}
                  valueStyle={{ color: gatewayUp ? '#0e8f83' : '#e5484d' }}
                />
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            <div className={styles.metricBody}>
              <div className={`${styles.metricIcon} ${styles.metricIconTeal}`}>
                <RiseOutlined />
              </div>
              <Statistic title="业务指标" value="待接入" prefix={<ClockCircleOutlined />} />
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            <div className={styles.metricBody}>
              <div className={`${styles.metricIcon} ${styles.metricIconAmber}`}>
                <AlertOutlined />
              </div>
              <Statistic title="实时告警" value="待接入" prefix={<ClockCircleOutlined />} />
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            <div className={styles.metricBody}>
              <div className={`${styles.metricIcon} ${styles.metricIconCyan}`}>
                <DatabaseOutlined />
              </div>
              <Statistic title="数据质量" value="待接入" prefix={<ClockCircleOutlined />} />
            </div>
          </Card>
        </Col>
      </Row>

      {healthQuery.isError && (
        <Alert
          type="warning"
          showIcon
          message="暂时无法连接本地网关"
          description="请确认网关已在 8080 端口启动。该问题不影响查看 B 端工程骨架。"
          action={
            <Button size="small" onClick={() => healthQuery.refetch()}>
              重新检查
            </Button>
          }
        />
      )}

      <section className={styles.readiness}>
        <div>
          <Typography.Title level={4}>运营能力接入状态</Typography.Title>
          <Typography.Paragraph type="secondary">
            人工工单、指标、报表、告警、数据质量与链路接口尚未进入正式 OpenAPI，因此不展示模拟业务数据。
          </Typography.Paragraph>
          {healthQuery.dataUpdatedAt > 0 && (
            <Typography.Text className={styles.checkedAt} type="secondary">
              最近一次网关检查：{dayjs(healthQuery.dataUpdatedAt).format('HH:mm:ss')}
            </Typography.Text>
          )}
        </div>
        <Space wrap>
          <Tag>人工确认台</Tag>
          <Tag>实时指标</Tag>
          <Tag>告警中心</Tag>
          <Tag>T+1 报表</Tag>
          <Tag>数据质量</Tag>
          <Tag>链路追溯</Tag>
        </Space>
      </section>
    </div>
  );
}
