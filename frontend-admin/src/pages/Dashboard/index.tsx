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
import { listAlerts, listDataQuality, listRealtimeMetrics } from '@/services/ops';
import pageStyles from '../page.less';
import styles from './index.less';

/**
 * 可信运行看板（Dashboard）。
 *
 * 网关健康、实时业务指标、开放告警数与最近数据质量均来自真实监控投影接口；
 * 数据由服务端决定，前端不伪造。网关状态每 60 秒轮询一次。
 */
export default function Dashboard() {
  // 网关健康轮询：60 秒刷新，失败自动重试 1 次，保证运营台能感知网关可用性变化。
  const healthQuery = useQuery({
    queryKey: ['gateway', 'health'],
    queryFn: getGatewayHealth,
    retry: 1,
    refetchInterval: 60_000,
  });

  // 分钟级实时业务指标：汇总最近窗口内各指标事件量，作为业务活跃度展示。
  const metricsQuery = useQuery({
    queryKey: ['ops', 'realtime-metrics'],
    queryFn: () => listRealtimeMetrics(),
    refetchInterval: 30_000,
  });

  // 开放告警数：仅统计待确认告警，供运营快速感知一致性风险。
  const alertsQuery = useQuery({
    queryKey: ['ops', 'alerts', 'OPEN'],
    queryFn: () => listAlerts('OPEN'),
    refetchInterval: 30_000,
  });

  // 最近数据质量：按上一自然日统计质量检查结果数。
  const qualityQuery = useQuery({
    queryKey: ['ops', 'data-quality', 'yesterday'],
    queryFn: () => listDataQuality(dayjs().subtract(1, 'day').format('YYYY-MM-DD')),
    refetchInterval: 60_000,
  });

  const totalMetrics =
    metricsQuery.data?.data.reduce((sum, item) => sum + item.value, 0) ?? undefined;
  const openAlertCount = alertsQuery.data?.data.items.length ?? undefined;
  const qualityResultCount = qualityQuery.data?.data.length ?? undefined;

  const healthCheckedAt = healthQuery.dataUpdatedAt;
  const hasHealthData = healthCheckedAt > 0;
  const gatewayUp = healthQuery.data?.status === 'UP';
  // 轮询失败时保留上次健康结果，但明确标注为过期状态，避免误报“运行中”。
  const gatewayCheckStale = hasHealthData && healthQuery.isRefetchError;

  return (
    <div className={pageStyles.page}>
      <div className={styles.heading}>
        <div>
          <Typography.Text type="secondary">
            当前仅展示已由正式契约定义的网关健康状态。
          </Typography.Text>
        </div>
        {/* 状态标签：优先呈现“过期”语义，其次按最新一次检查结果区分正常/未知/检查中。 */}
        <Tag
          color={
            gatewayCheckStale
              ? 'warning'
              : gatewayUp
                ? 'success'
                : healthQuery.isLoading
                  ? 'processing'
                  : 'default'
          }
        >
          {gatewayCheckStale
            ? '检查超时，展示上次状态'
            : gatewayUp
              ? '网关运行中'
              : healthQuery.isLoading
                ? '检查中'
                : '状态未知'}
        </Tag>
      </div>

      {/* 指标卡片区：第一张为真实网关健康状态（首次加载显示骨架屏），其余三张为待接入占位。
          值着色遵循状态语义：正常青绿、异常珊瑚、过期琥珀，颜色值见 theme.ts。 */}
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
                  value={gatewayCheckStale ? '上次正常' : gatewayUp ? '正常' : '不可用'}
                  valueStyle={{
                    color: gatewayCheckStale ? '#ae7f35' : gatewayUp ? '#2f8d7e' : '#b35c60',
                  }}
                />
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            {metricsQuery.isLoading ? (
              <Skeleton active paragraph={{ rows: 1 }} title={false} />
            ) : (
              <div className={styles.metricBody}>
                <div className={`${styles.metricIcon} ${styles.metricIconTeal}`}>
                  <RiseOutlined />
                </div>
                <Statistic title="业务指标" value={totalMetrics ?? 0} prefix={<ClockCircleOutlined />} />
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            {alertsQuery.isLoading ? (
              <Skeleton active paragraph={{ rows: 1 }} title={false} />
            ) : (
              <div className={styles.metricBody}>
                <div className={`${styles.metricIcon} ${styles.metricIconAmber}`}>
                  <AlertOutlined />
                </div>
                <Statistic title="开放告警" value={openAlertCount ?? 0} prefix={<AlertOutlined />} />
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className={styles.metricCard}>
            {qualityQuery.isLoading ? (
              <Skeleton active paragraph={{ rows: 1 }} title={false} />
            ) : (
              <div className={styles.metricBody}>
                <div className={`${styles.metricIcon} ${styles.metricIconCyan}`}>
                  <DatabaseOutlined />
                </div>
                <Statistic title="数据质量检查" value={qualityResultCount ?? 0} prefix={<DatabaseOutlined />} />
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 网关不可达提示：区分“从未成功”与“历史成功但最近轮询失败”两种文案，并支持手动重新检查。 */}
      {(healthQuery.isError || gatewayCheckStale) && (
        <Alert
          type="warning"
          showIcon
          message={hasHealthData ? '网关健康检查暂未更新' : '暂时无法连接本地网关'}
          description={
            hasHealthData
              ? `当前展示 ${dayjs(healthCheckedAt).format('HH:mm:ss')} 的最近一次状态，请确认网关仍在运行。`
              : '请确认网关已在 8080 端口启动。该问题不影响查看 B 端工程骨架。'
          }
          action={
            <Button size="small" onClick={() => healthQuery.refetch()}>
              重新检查
            </Button>
          }
        />
      )}

      {/* 运营能力接入状态：明确告知哪些能力尚未进入正式 OpenAPI，不做模拟数据承诺。 */}
      <section className={styles.readiness}>
        <div>
          <Typography.Title level={4}>运营能力接入状态</Typography.Title>
          <Typography.Paragraph type="secondary">
            实时指标、告警、数据质量与人工工单已接入正式 OpenAPI；链路追溯与演示任务仍待契约补齐。
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
