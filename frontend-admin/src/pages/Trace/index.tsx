import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Input, Space, Tag, Timeline, Typography, App } from 'antd';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { getOpsTransactionTrace, type TraceSpanItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 链路阶段状态标签；仅按业务中心可核验的资金事实着色，未知阶段不使用成功色。 */
const spanStatusColor = (status: string): string => {
  if (status === 'SUCCESS') return 'green';
  if (status === 'PROCESSING' || status === 'PENDING') return 'blue';
  if (status === 'MANUAL_REVIEW') return 'red';
  return 'orange';
};

/** 将链路编号输入限制为交易或链路编号格式，避免把敏感内容带进查询。 */
const isLikelyId = (value: string): boolean => /^[A-Za-z0-9-]{1,64}$/.test(value);

/**
 * 链路追溯页面。
 *
 * 按交易编号查询业务中心可核验的资金事实阶段（统一交易受理、TCC 全局事务、终态事件发布），
 * 以脱敏 Span 时间线展示。完整跨服务 Trace（网关、用户中心、账户中心、AI）归阶段七 OTel 集成，
 * 本页面只展示已核验事实，未知阶段不显示为成功。
 */
export default function Trace() {
  const { message } = App.useApp();
  const [input, setInput] = useState('');
  const [queryId, setQueryId] = useState<string | undefined>(undefined);

  const { data, isFetching, isError, error, refetch } = useQuery({
    queryKey: ['ops-transaction-trace', queryId],
    queryFn: () => getOpsTransactionTrace(queryId!),
    enabled: !!queryId,
    retry: false,
  });

  const spans: TraceSpanItem[] = data?.data ?? [];

  const timelineItems = spans.map((span) => ({
    color: spanStatusColor(span.status),
    children: (
      <div>
        <Space size="small" wrap>
          <Typography.Text strong>{span.operation}</Typography.Text>
          <Tag>{span.service}</Tag>
          <Tag color={spanStatusColor(span.status)}>{span.status}</Tag>
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
      message.warning('请输入交易编号');
      return;
    }
    if (!isLikelyId(value)) {
      message.error('交易编号格式不正确');
      return;
    }
    setQueryId(value);
  };

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="链路检索">
        <Input
          className={pageStyles.searchInput}
          aria-label="交易编号"
          placeholder="输入交易编号"
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
          脱敏 Span 时间线
        </Typography.Title>
        {isError && (
          <Alert
            type="error"
            showIcon
            message="链路查询失败"
            description={(error as Error)?.message ?? '请确认交易编号后重试'}
            action={<Button size="small" onClick={() => refetch()}>重试</Button>}
            style={{ marginBottom: 16 }}
          />
        )}
        {spans.length > 0 ? (
          <Timeline items={timelineItems} />
        ) : (
          <Empty description={queryId ? '未查询到该交易的链路片段' : '输入交易编号后按时间展示各服务 Span'} />
        )}
      </section>
    </main>
  );
}
