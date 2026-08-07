import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Input, Space, Tag, Timeline, Typography, App } from 'antd';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { getOpsTraceByTraceId, getOpsTransactionTrace, type TraceSpanItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 链路阶段状态标签；仅按业务中心可核验的资金事实着色，未知阶段不使用成功色。 */
const spanStatusColor = (status: string): string => {
  if (status === 'SUCCESS') return 'green';
  if (status === 'PROCESSING' || status === 'PENDING') return 'blue';
  if (status === 'MANUAL_REVIEW') return 'red';
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

  const { data, isFetching, isError, error, refetch } = useQuery({
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

  const timelineItems = spans.map((span) => ({
    color: spanStatusColor(span.status),
    children: (
      <div>
        <Space size="small" wrap>
          <Typography.Text strong>{span.operation}</Typography.Text>
          <Tag color={serviceColor(span.service)}>{span.service}</Tag>
          <Tag color={spanStatusColor(span.status)}>{span.status}</Tag>
          {span.transactionId && <Tag>{span.transactionId}</Tag>}
        </Space>
        <div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {span.detail} · {span.traceId} · {new Date(span.occurredAt).toLocaleString()}
          </Typography.Text>
        </div>
      </div>
    ),
  }));

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
        <Button type="primary" loading={isFetching} onClick={submit}>
          查询链路
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="链路时间线">
        <Typography.Title level={4} className={pageStyles.panelTitle}>
          脱敏跨服务 Span 时间线
        </Typography.Title>
        {isError && (
          <Alert
            type="error"
            showIcon
            message="链路查询失败"
            description={(error as Error)?.message ?? '请确认输入后重试'}
            action={<Button size="small" onClick={() => refetch()}>重试</Button>}
            style={{ marginBottom: 16 }}
          />
        )}
        {spans.length > 0 ? (
          <Timeline items={timelineItems} />
        ) : (
          <Empty description={emptyText} />
        )}
      </section>
    </main>
  );
}
