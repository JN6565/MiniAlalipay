import { useAccess } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import { decideManualCase, listManualCases, type ManualCaseItem } from '@/services/ops';
import pageStyles from '../page.less';

/** 工单处置动作类型。 */
type CaseDecision = 'CLAIM' | 'RESOLVE' | 'REOPEN' | 'CLOSE';

/** 工单状态中文标签。 */
const STATUS_LABEL: Record<string, string> = {
  OPEN: '待处理',
  CLAIMED: '已领取',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
};

/** 工单类型中文标签。 */
const TYPE_LABEL: Record<string, string> = {
  RISK_PRECHECK: '风险预检',
  TRANSACTION_RECOVERY: '事务恢复',
};

/**
 * 人工确认台。
 *
 * 读取真实人工工单，管理员与运营人员可领取、解决、关闭；处置携带服务端幂等键与 CAS 版本。
 * 页面只展示服务端确定的脱敏状态，不直接修改资金事实。
 */
export default function ManualCases() {
  const { message } = App.useApp();
  const access = useAccess();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<string>();
  const [action, setAction] = useState<{ case: ManualCaseItem; decision: CaseDecision }>();
  const [detail, setDetail] = useState<ManualCaseItem>();
  const [form] = Form.useForm();
  // 服务端仅提供正向游标，使用游标栈回退上一页；空字符串表示第一页。
  const [cursor, setCursor] = useState<string>();
  const [cursorStack, setCursorStack] = useState<string[]>([]);

  const casesQuery = useQuery({
    queryKey: ['manual-cases', status, cursor],
    queryFn: () => listManualCases(status, undefined, cursor),
  });

  const nextCursor = casesQuery.data?.data.nextCursor ?? null;

  function changeStatus(value?: string) {
    setStatus(value);
    setCursor(undefined);
    setCursorStack([]);
  }

  function goNextPage() {
    if (!nextCursor) return;
    setCursorStack((prev) => [...prev, cursor ?? '']);
    setCursor(nextCursor);
  }

  function goPrevPage() {
    const prevCursor = cursorStack.at(-1);
    if (prevCursor === undefined) return;
    setCursorStack((prev) => prev.slice(0, -1));
    setCursor(prevCursor === '' ? undefined : prevCursor);
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const target = action;
      if (!target) throw new Error('缺少处置对象');
      const values = await form.validateFields();
      const needsEvidence = target.decision === 'RESOLVE' || target.decision === 'CLOSE';
      return decideManualCase(
        target.case.caseId,
        target.decision,
        target.case.version,
        values.reason,
        needsEvidence ? values.evidence : undefined,
      );
    },
    onSuccess: () => {
      message.success('工单处置成功');
      setAction(undefined);
      queryClient.invalidateQueries({ queryKey: ['manual-cases'] });
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '工单处置失败');
    },
  });

  /** 根据当前状态推断可执行的处置动作。 */
  function nextDecision(statusValue: string): CaseDecision | undefined {
    if (statusValue === 'OPEN') return 'CLAIM';
    if (statusValue === 'CLAIMED') return 'RESOLVE';
    if (statusValue === 'RESOLVED') return 'CLOSE';
    return undefined;
  }

  /** 处置证据或理由为空时的占位符。 */
  function placeholder(value: string | null | undefined): string {
    return value ? value : '—';
  }

  const columns: ColumnsType<ManualCaseItem> = [
    {
      title: '工单号',
      dataIndex: 'caseId',
      render: (value: string, record) => (
        <Button type="link" size="small" onClick={() => setDetail(record)}>
          {value}
        </Button>
      ),
    },
    { title: '类型', dataIndex: 'caseType', render: (value: string) => TYPE_LABEL[value] ?? value },
    { title: '规则命中', dataIndex: 'reasonCode', render: (value: string) => value ?? '—' },
    { title: '状态', dataIndex: 'status', render: (value: string) => STATUS_LABEL[value] ?? value },
    { title: '操作者', dataIndex: 'operatorId', render: (value: string | null) => placeholder(value) },
    { title: '处置时间', dataIndex: 'updatedAt' },
    { title: '创建时间', dataIndex: 'createdAt' },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        if (!access.canOperateManualCases) return null;
        const decision = nextDecision(record.status);
        if (!decision) return null;
        const label = decision === 'CLAIM' ? '领取' : decision === 'RESOLVE' ? '解决' : '关闭';
        return (
          <Button size="small" onClick={() => setAction({ case: record, decision })}>
            {label}
          </Button>
        );
      },
    },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="工单筛选">
        <Select
          aria-label="工单状态"
          allowClear
          placeholder="工单状态"
          style={{ width: 180 }}
          options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          onChange={changeStatus}
        />
        <Button type="primary" onClick={() => casesQuery.refetch()}>
          查询
        </Button>
        <Space>
          <Button onClick={goPrevPage} disabled={cursorStack.length === 0}>
            上一页
          </Button>
          <Button onClick={goNextPage} disabled={!nextCursor}>
            下一页
          </Button>
        </Space>
      </section>
      <section className={pageStyles.panel} aria-label="工单列表">
        <Table<ManualCaseItem>
          rowKey="caseId"
          columns={columns}
          dataSource={casesQuery.data?.data.items ?? []}
          loading={casesQuery.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                description={casesQuery.isError
                  ? '加载失败，请确认网关已启动'
                  : status
                    ? '暂无该状态工单'
                    : '暂无待处理工单'}
              />
            ),
          }}
          scroll={{ x: 960 }}
        />
      </section>
      <Modal
        title={action ? `处置工单（${STATUS_LABEL[action.case.status] ?? action.case.status}）` : ''}
        open={!!action}
        confirmLoading={mutation.isPending}
        onOk={() => mutation.mutate()}
        onCancel={() => setAction(undefined)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ reason: '', evidence: '' }}>
          {action?.decision !== 'CLAIM' && (
            <>
              <Form.Item name="reason" label="处置理由" rules={[{ required: true, message: '请输入处置理由' }]}>
                <Input.TextArea rows={2} maxLength={500} />
              </Form.Item>
              {(action?.decision === 'RESOLVE' || action?.decision === 'CLOSE') && (
                <Form.Item name="evidence" label="处置证据" rules={[{ required: true, message: '请输入处置证据' }]}>
                  <Input.TextArea rows={3} maxLength={2000} />
                </Form.Item>
              )}
            </>
          )}
          {action?.decision === 'CLAIM' && (
            <Form.Item label="领取确认">
              <Input.TextArea rows={2} placeholder="领取开放工单，可暂不填写理由" disabled />
            </Form.Item>
          )}
        </Form>
      </Modal>
      <Drawer
        title={detail ? `工单详情（${TYPE_LABEL[detail.caseType] ?? detail.caseType}）` : ''}
        open={!!detail}
        width={560}
        onClose={() => setDetail(undefined)}
      >
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="工单号">{detail.caseId}</Descriptions.Item>
            <Descriptions.Item label="工单类型">{TYPE_LABEL[detail.caseType] ?? detail.caseType}</Descriptions.Item>
            <Descriptions.Item label="主体类型">{detail.subjectType}</Descriptions.Item>
            <Descriptions.Item label="主体">{detail.subjectId}</Descriptions.Item>
            <Descriptions.Item label="状态">{STATUS_LABEL[detail.status] ?? detail.status}</Descriptions.Item>
            <Descriptions.Item label="规则命中">{detail.reasonCode}</Descriptions.Item>
            <Descriptions.Item label="操作者">{placeholder(detail.operatorId)}</Descriptions.Item>
            <Descriptions.Item label="处置理由">{placeholder(detail.lastReason)}</Descriptions.Item>
            <Descriptions.Item label="处置证据">{placeholder(detail.evidenceReference)}</Descriptions.Item>
            <Descriptions.Item label="版本">{detail.version}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="处置时间">{detail.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </main>
  );
}
