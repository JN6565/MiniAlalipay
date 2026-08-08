import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Input, Space, Switch, Tag, Timeline, Typography, App } from 'antd';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { getOpsTraceByTraceId, getOpsTransactionTrace, type TraceSpanItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 链路阶段状态标签；仅按业务中心可核验的资金事实着色，未知阶段不使用成功色。 */
const spanStatusColor = (status: string): string => {
  if (status === 'SUCCESS' || status === 'PUBLISHED') return 'green';
  if (status === 'PROCESSING' || status === 'PENDING' || status === 'COMPENSATING'
    || status === 'COMMITTING' || status === 'ROLLING_BACK') return 'blue';
  if (status === 'MANUAL_REVIEW' || status === 'ERROR' || status === 'DEAD') return 'red';
  if (status === 'CANCELLED' || status === 'REVERSED') return 'orange';
  return 'orange';
};

/** 服务来源标签着色，按归属服务区分跨服务链路。 */
const serviceColor = (service: string): string => {
  switch (service) {
    case 'business-center':
      return 'blue';
    case 'account-center':
      return 'green';
    case 'user-center':
      return 'purple';
    case 'ai-service':
      return 'orange';
    default:
      return 'default';
  }
};

/** 技术证据中的服务名称：保留服务编码，补充其承担的业务职责，便于排查跨服务链路。 */
const serviceLabel = (service: string): string => {
  const names: Record<string, string> = {
    'business-center': '支付业务服务',
    'account-center': '账户与账本服务',
    'user-center': '用户服务',
    'ai-service': 'AI 服务',
  };
  return `${names[service] ?? '未知服务'}（${service}）`;
};

/** 技术证据展示的状态文本；保持业务状态含义，但避免直接暴露英文枚举值。 */
const technicalStatusLabel = (status: string): string => {
  switch (status) {
    case 'SUCCESS':
      return '已完成';
    case 'PROCESSING':
      return '处理中';
    case 'PENDING':
      return '待发布';
    case 'COMMITTING':
      return '提交中';
    case 'ROLLING_BACK':
      return '回滚中';
    case 'COMPENSATING':
      return '补偿中';
    case 'MANUAL_REVIEW':
      return '待人工处理';
    case 'ERROR':
      return '失败';
    case 'DEAD':
      return '投递失败';
    case 'CANCELLED':
      return '已取消';
    case 'REVERSED':
      return '已冲正';
    case 'PUBLISHED':
      return '已发布';
    default:
      return status;
  }
};

/** 服务端 Span 操作名到业务步骤的默认翻译；未知操作原样保留服务端中文操作名。 */
const stepNameByOperation: Record<string, string> = {
  统一交易受理: '支付受理',
  'TCC 全局事务': '资金处理',
  终态事件发布: '终态发布',
  账本过账事件: '账本记账',
  用户中心审计: '用户核验',
  'AI 工具调用': 'AI 辅助',
  'AI 审计': 'AI 审计',
};

/** 状态反映业务结果的 Span 操作名；仅这些节点的状态标签在默认业务视图展示。 */
const businessStateOperations = new Set(['统一交易受理', 'TCC 全局事务']);

/** 链路片段状态标签；PENDING 表示 Outbox 事件待投递，属技术状态，默认业务视图不展示。 */
const spanStatusLabels: Record<string, string> = {
  SUCCESS: '成功',
  PROCESSING: '处理中',
  PENDING: '待发布',
  COMMITTING: '提交中',
  ROLLING_BACK: '回滚中',
  COMPENSATING: '补偿中',
  MANUAL_REVIEW: '待人工处理',
  ERROR: '失败',
  DEAD: '投递失败',
  CANCELLED: '已取消',
  REVERSED: '已冲正',
  PUBLISHED: '已发布',
};

/** 把服务端 Span 翻译为业务步骤名；交易成败与是否待人工由交易/资金状态节点体现，事件投递状态归技术证据。 */
function businessStep(span: TraceSpanItem): string {
  return stepNameByOperation[span.operation] ?? span.operation;
}

/** 链路编号为 32 位十六进制；其余输入按交易号查询。 */
const isTraceId = (value: string): boolean => /^[a-fA-F0-9]{32}$/.test(value);

/**
 * 链路追溯页面。
 *
 * 支持按交易号或链路编号（32 位 hex）查询跨服务脱敏链路片段（业务中心、账户账本、用户审计、AI），
 * 以 service 标签区分来源、transactionId 标注片段归属。完整 OTel 跨服务 Trace 归阶段七集成。
 */
export default function Trace() {
  const { message } = App.useApp();
  const [input, setInput] = useState('');
  const [query, setQuery] = useState<{ id: string; mode: 'transaction' | 'trace' }>();
  // 技术证据开关：默认关闭，打开后展示服务职责、事件与状态等跨服务链路明细。
  const [showTech, setShowTech] = useState(false);

  const { data, isFetching } = useQuery({
    queryKey: ['ops', 'trace', query?.mode, query?.id],
    queryFn: () => {
      if (!query) throw new Error('缺少查询编号');
      return query.mode === 'trace'
        ? getOpsTraceByTraceId(query.id)
        : getOpsTransactionTrace(query.id);
    },
    enabled: !!query,
    retry: false,
  });

  const spans: TraceSpanItem[] = data?.data ?? [];

  const timelineItems = spans.map((span) => {
    const step = businessStep(span);
    const statusText = spanStatusLabels[span.status] ?? span.status;
    // 只有交易与资金状态反映业务结果；Outbox 投递、审计等状态属技术字段，默认业务视图不展示。
    const isBusinessState = businessStateOperations.has(span.operation);
    return {
      color: spanStatusColor(span.status),
      children: (
        <div>
          <Space size="small" wrap>
            <Typography.Text strong>{step}</Typography.Text>
            {isBusinessState && <Tag color={spanStatusColor(span.status)}>{statusText}</Tag>}
          </Space>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {new Date(span.occurredAt).toLocaleString()}
            </Typography.Text>
          </div>
          {showTech && (
            <>
              <Space size="small" wrap style={{ marginTop: 4 }}>
                <Tag color={serviceColor(span.service)}>服务：{serviceLabel(span.service)}</Tag>
                <Tag>原始事件：{span.operation}</Tag>
                <Tag color={spanStatusColor(span.status)}>事件状态：{technicalStatusLabel(span.status)}</Tag>
                {span.transactionId && <Tag>交易号：{span.transactionId}</Tag>}
              </Space>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  事件详情：{span.detail || '无'}；链路号：{span.traceId}
                </Typography.Text>
              </div>
            </>
          )}
        </div>
      ),
    };
  });

  const submit = () => {
    const value = input.trim();
    if (!value) {
      message.warning('请输入交易号或链路编号');
      return;
    }
    if (value.length > 64) {
      message.error('交易号或链路编号格式不正确');
      return;
    }
    setQuery(isTraceId(value) ? { id: value, mode: 'trace' } : { id: value, mode: 'transaction' });
  };

  const emptyText = !query
    ? '输入交易号或链路编号后按时间展示各服务 Span'
    : query.mode === 'trace'
      ? '未查询到该链路编号的跨服务片段'
      : '未查询到该交易的链路片段';

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="链路检索">
        <Input
          className={pageStyles.searchInput}
          aria-label="交易号或链路编号"
          placeholder="输入交易号或链路编号"
          prefix={<SearchOutlined />}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={submit}
        />
        <Button type="primary" className="admin-btn-query" loading={isFetching} onClick={submit}>
          查询链路
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="链路时间线">
        <div
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}
        >
          <Typography.Title level={4} className={pageStyles.panelTitle}>
            脱敏跨服务 Span 时间线
          </Typography.Title>
          <Space size="small">
            <Switch size="small" checked={showTech} onChange={setShowTech} aria-label="查看技术证据" />
            <Typography.Text type="secondary">查看技术证据</Typography.Text>
          </Space>
        </div>
        {spans.length > 0 ? (
          <Timeline items={timelineItems} />
        ) : (
          <Empty description={emptyText} />
        )}
      </section>
    </main>
  );
}
