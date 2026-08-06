import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Descriptions, Select, Space, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { getOpsTransaction, listOpsTransactions, type OpsTransactionItem } from '@/services/ops';
import { formatAmountFen } from '@/utils/amount';
import pageStyles from '../page.less';

/** 交易状态展示标签，仅映射服务端确定的权威状态，未知结果不显示为成功。 */
const statusLabels: Record<string, { text: string; color: string }> = {
  PROCESSING: { text: '处理中', color: 'blue' },
  SUCCESS: { text: '成功', color: 'green' },
  FAILED: { text: '失败', color: 'red' },
  CANCELLED: { text: '已取消', color: 'orange' },
  COMPENSATING: { text: '补偿中', color: 'orange' },
  REVERSED: { text: '已冲正', color: 'purple' },
  MANUAL_REVIEW: { text: '人工审核', color: 'red' },
  REJECTED: { text: '已拒绝', color: 'red' },
  EXPIRED: { text: '已过期', color: 'default' },
};

/** 业务类型展示标签。 */
const businessLabels: Record<string, string> = {
  TRANSFER: '转账',
  QR_PAY: '扫码付款',
  CREDIT_PAY: '花呗扫码',
  CREDIT_REPAY: '信用还款',
  RECHARGE: '模拟充值',
  REFUND: '受控退款',
};

/** 状态与业务类型筛选项；与 OpenAPI 枚举一致。 */
const statusOptions = Object.keys(statusLabels);
const businessOptions = Object.keys(businessLabels);

/**
 * 交易查询与回执页面。
 *
 * 按游标分页查询全平台脱敏交易摘要，点击行查看单笔交易详情与关联资金事实（TCC 全局、Outbox 事件、活动工单）。
 * 展示约定：金额遵循整数分并在展示边界转换；发起人已由服务端脱敏；未知结果不显示为成功。
 */
export default function Transactions() {
  const [status, setStatus] = useState<string | undefined>();
  const [businessType, setBusinessType] = useState<string | undefined>();
  // 游标栈用于上一页；nextCursor 为空表示已到最新一页。
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [detailId, setDetailId] = useState<string | null>(null);

  const { data, isFetching } = useQuery({
    queryKey: ['ops-transactions', status, businessType, cursor],
    queryFn: () => listOpsTransactions({ status, businessType, cursor }),
  });

  const page = data?.data;
  const rows: OpsTransactionItem[] = page?.items ?? [];

  const { data: detail, isFetching: detailLoading } = useQuery({
    queryKey: ['ops-transaction-detail', detailId],
    queryFn: () => getOpsTransaction(detailId!),
    enabled: !!detailId,
    retry: false,
  });

  const refresh = () => {
    setCursorStack([]);
    setCursor(undefined);
  };

  const nextPage = () => {
    if (!page?.nextCursor) return;
    setCursorStack((stack) => [...stack, cursor ?? '']);
    setCursor(page.nextCursor);
  };

  const prevPage = () => {
    setCursorStack((stack) => {
      if (stack.length === 0) return stack;
      const next = stack.slice(0, -1);
      setCursor(next[next.length - 1] || undefined);
      return next;
    });
  };

  const columns: TableProps<OpsTransactionItem>['columns'] = useMemo(
    () => [
      {
        title: '交易编号',
        dataIndex: 'transactionId',
        ellipsis: true,
        width: 200,
      },
      {
        title: '业务类型',
        dataIndex: 'businessType',
        width: 110,
        render: (value: string) => businessLabels[value] ?? value,
      },
      {
        title: '金额（元）',
        dataIndex: 'amountFen',
        width: 110,
        align: 'right',
        render: (value: number) => formatAmountFen(value),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: string) => {
          const label = statusLabels[value] ?? { text: value, color: 'default' };
          return <Tag color={label.color}>{label.text}</Tag>;
        },
      },
      {
        title: '发起人',
        dataIndex: 'initiatorMasked',
        width: 120,
      },
      {
        title: '来源订单',
        dataIndex: 'sourceOrderId',
        ellipsis: true,
        width: 160,
      },
      {
        title: '风险',
        dataIndex: 'riskLevel',
        width: 80,
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        width: 180,
        render: (value: string) => new Date(value).toLocaleString(),
      },
    ],
    [],
  );

  const detailItem = detail?.data?.transaction;
  const detailSpans = [
    { key: 'transactionId', label: '交易编号', children: detailItem?.transactionId ?? '-' },
    { key: 'businessType', label: '业务类型', children: detailItem ? (businessLabels[detailItem.businessType] ?? detailItem.businessType) : '-' },
    { key: 'status', label: '交易状态', children: detailItem ? (statusLabels[detailItem.status]?.text ?? detailItem.status) : '-' },
    { key: 'amount', label: '金额（元）', children: detailItem ? formatAmountFen(detailItem.amountFen) : '-' },
    { key: 'source', label: '来源', children: detailItem ? `${detailItem.sourceType}/${detailItem.sourceOrderId}` : '-' },
    { key: 'initiator', label: '发起人', children: detailItem?.initiatorMasked ?? '-' },
    { key: 'fundingSource', label: '资金来源', children: detail?.data?.fundingSource ?? '-' },
    { key: 'riskLevel', label: '风险等级', children: detailItem?.riskLevel ?? '-' },
    { key: 'tccStatus', label: 'TCC 全局状态', children: detail?.data?.tccStatus ?? '-' },
    { key: 'tccRetry', label: 'TCC 重试次数', children: detail?.data?.tccRetryCount ?? '-' },
    { key: 'outbox', label: '最新终态事件', children: detail?.data?.latestOutboxEventType ?? '-' },
    { key: 'outboxStatus', label: '事件投递状态', children: detail?.data?.outboxStatus ?? '-' },
    { key: 'manualCase', label: '活动人工工单', children: detail?.data?.activeManualCaseId ?? '-' },
    { key: 'traceId', label: '链路编号', children: detailItem?.traceId ?? '-' },
    { key: 'createdAt', label: '创建时间', children: detailItem ? new Date(detailItem.createdAt).toLocaleString() : '-' },
    { key: 'updatedAt', label: '最近更新', children: detailItem ? new Date(detailItem.updatedAt).toLocaleString() : '-' },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="交易检索">
        <Space wrap>
          <Select
            allowClear
            placeholder="交易状态"
            value={status}
            style={{ width: 140 }}
            onChange={(value) => { setStatus(value); refresh(); }}
            options={statusOptions.map((value) => ({ value, label: statusLabels[value].text }))}
          />
          <Select
            allowClear
            placeholder="业务类型"
            value={businessType}
            style={{ width: 140 }}
            onChange={(value) => { setBusinessType(value); refresh(); }}
            options={businessOptions.map((value) => ({ value, label: businessLabels[value] }))}
          />
          <Button type="primary" onClick={refresh}>刷新</Button>
        </Space>
      </section>
      <section className={pageStyles.panel} aria-label="交易列表">
        <Table<OpsTransactionItem>
          rowKey="transactionId"
          size="middle"
          loading={isFetching}
          columns={columns}
          dataSource={rows}
          pagination={false}
          onRow={(record) => ({
            onClick: () => setDetailId(record.transactionId),
            style: { cursor: 'pointer' },
          })}
          locale={{ emptyText: '暂无交易记录，请调整筛选条件' }}
        />
        <Space style={{ marginTop: 16, justifyContent: 'flex-end', width: '100%' }}>
          <Button disabled={cursorStack.length === 0} onClick={prevPage}>上一页</Button>
          <Typography.Text type="secondary">{rows.length} 条</Typography.Text>
          <Button disabled={!page?.nextCursor} onClick={nextPage}>下一页</Button>
        </Space>
      </section>
      <Drawer
        title="交易唯一事实详情"
        open={!!detailId}
        onClose={() => setDetailId(null)}
        width={560}
        loading={detailLoading}
      >
        <Descriptions bordered size="small" column={1} items={detailSpans} />
        <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
          以上字段均来自服务端统一交易与关联资金事实，不包含确认令牌或支付密码等敏感材料。
        </Typography.Paragraph>
      </Drawer>
    </main>
  );
}
