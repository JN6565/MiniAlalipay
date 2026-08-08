import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { useAccess, useModel } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Descriptions, Divider, Drawer, Empty, Form, Input, Modal, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
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

/** 被复核主体的中文名称，原始编码只用于接口与审计关联，不直接作为运营文案。 */
const SUBJECT_TYPE_LABEL: Record<string, string> = {
  FUND_TRANSACTION: '资金交易',
  TRANSFER_DRAFT: '转账草稿',
  QR_PAY_ORDER: '扫码支付订单',
  PERSONAL_QR_ORDER: '个人收款订单',
  COLLECTION_REQUEST_ORDER: '固定收款请求订单',
};

/** 已知风险与事务恢复原因的中文名称。 */
const REASON_LABEL: Record<string, string> = {
  SUCCESS_FACT_MISMATCH: '成功事实不一致',
  'R-02_PAYMENT_AMOUNT_EXCEEDS_LIMIT': '单笔金额超过限额',
  'R-03_HIGH_FREQUENCY_TRADING': '短时间内交易频繁',
  'R-04_LARGE_AMOUNT': '大额交易提示',
  'R-05_NEW_PAYEE': '首次向该收款人发起大额交易',
  'R-06_REPEATED_PAYMENT_FEATURE': '检测到重复付款特征',
};

/** 工单处置动作中文标签。 */
const ACTION_LABEL: Record<CaseDecision, string> = {
  CLAIM: '领取',
  RESOLVE: '解决',
  REOPEN: '重开',
  CLOSE: '关闭',
};

/**
 * 人工确认台。
 *
 * 读取真实人工工单，管理员与运营人员可领取、解决、重开、关闭；处置携带服务端幂等键与 CAS 版本。
 * 页面只展示服务端确定的脱敏状态，不直接修改资金事实。
 */
export default function ManualCases() {
  const { message } = App.useApp();
  const access = useAccess();
  const queryClient = useQueryClient();
  // 当前运营身份（来自网关 /auth/me）；userId 用于识别工单领取人是否当前操作者。
  const { initialState } = useModel('@@initialState');
  const currentAdmin = initialState?.currentAdmin;
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
  const rows = casesQuery.data?.data.items ?? [];

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

  /** 按工单状态返回可执行的处置动作；服务端状态机与领取人校验仍是最终授权方。 */
  function possibleActions(statusValue: string): CaseDecision[] {
    if (statusValue === 'OPEN') return ['CLAIM'];
    if (statusValue === 'CLAIMED') return ['RESOLVE'];
    if (statusValue === 'RESOLVED') return ['REOPEN', 'CLOSE'];
    return [];
  }

  /** 处置证据或理由为空时的占位符。 */
  function placeholder(value: string | null | undefined): string {
    return value ? value : '—';
  }

  /** 将服务端 UTC 时间转换为运营人员可读的本地时间。 */
  function formatTime(value: string): string {
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  /** 未登记的内部编码不直接暴露给运营人员，避免英文状态破坏阅读体验。 */
  function labelOf(dictionary: Record<string, string>, value: string | null | undefined, fallback: string): string {
    if (!value) return '—';
    return dictionary[value] ?? fallback;
  }

  const columns: ColumnsType<ManualCaseItem> = [
    {
      title: '工单号',
      dataIndex: 'caseId',
      render: (value: string, record) => (
        <Button type="link" size="small" style={{ height: 'auto', overflowWrap: 'anywhere', whiteSpace: 'normal' }} onClick={() => setDetail(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: '类型', dataIndex: 'caseType',
      render: (value: string) => TYPE_LABEL[value] ?? '其他工单',
    },
    {
      title: '规则命中', dataIndex: 'reasonCode',
      render: (value: string) => labelOf(REASON_LABEL, value, '未定义触发原因'),
    },
    {
      title: '状态', dataIndex: 'status',
      render: (value: string) => STATUS_LABEL[value] ?? '未知状态',
    },
    {
      title: '操作者', dataIndex: 'operatorId',
      render: (value: string | null) => placeholder(value),
    },
    { title: '处置时间', dataIndex: 'updatedAt', render: formatTime },
    { title: '创建时间', dataIndex: 'createdAt', render: formatTime },
    {
      title: '操作',
      key: 'actions',
      width: 116,
      render: (_, record) => {
        if (!access.canOperateManualCases) return null;
        return (
          <Space wrap>
            {possibleActions(record.status).map((decision) => {
              // 领取动作任何运营都可执行；解决/重开/关闭仅限工单领取人（后端以此校验），非领取人不展示必然失败的操作。
              if (decision !== 'CLAIM' && record.operatorId !== currentAdmin?.userId) return null;
              return (
                <Button
                  key={decision}
                  size="small"
                  onClick={() => setAction({ case: record, decision })}
                >
                  {ACTION_LABEL[decision]}
                </Button>
              );
            })}
          </Space>
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
        <Button type="primary" className="admin-btn-query" onClick={() => casesQuery.refetch()}>
          查询
        </Button>
      </section>
      <section className={`${pageStyles.panel} ${pageStyles.noHorizontalScroll}`} aria-label="工单列表">
        <Table<ManualCaseItem>
          rowKey="caseId"
          columns={columns}
          dataSource={rows}
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
        />
        <Space style={{ marginTop: 16, justifyContent: 'flex-end', width: '100%' }}>
          <Button ghost disabled={cursorStack.length === 0} icon={<LeftOutlined />} onClick={goPrevPage}>
            上一页
          </Button>
          <Typography.Text type="secondary">{rows.length} 条</Typography.Text>
          <Button ghost disabled={!nextCursor} onClick={goNextPage}>
            下一页 <RightOutlined />
          </Button>
        </Space>
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
        width={640}
        onClose={() => setDetail(undefined)}
      >
        {detail && (
          <>
            <Typography.Title level={5}>工单概况</Typography.Title>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="工单号">{detail.caseId}</Descriptions.Item>
              <Descriptions.Item label="工单类型">{labelOf(TYPE_LABEL, detail.caseType, '未定义工单类型')}</Descriptions.Item>
              <Descriptions.Item label="当前状态">{labelOf(STATUS_LABEL, detail.status, '未定义状态')}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="最近更新时间">{formatTime(detail.updatedAt)}</Descriptions.Item>
            </Descriptions>

            <Divider />
            <Typography.Title level={5}>恢复上下文</Typography.Title>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="复核对象类型">{labelOf(SUBJECT_TYPE_LABEL, detail.subjectType, '未定义业务对象')}</Descriptions.Item>
              <Descriptions.Item label="复核对象编号">{detail.subjectId}</Descriptions.Item>
              <Descriptions.Item label="触发原因">{labelOf(REASON_LABEL, detail.reasonCode, '未定义触发原因')}</Descriptions.Item>
            </Descriptions>

            <Divider />
            <Typography.Title level={5}>处置记录</Typography.Title>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="最近操作者">{placeholder(detail.operatorId)}</Descriptions.Item>
              <Descriptions.Item label="处置理由">{placeholder(detail.lastReason)}</Descriptions.Item>
              <Descriptions.Item label="处置证据">{placeholder(detail.evidenceReference)}</Descriptions.Item>
              <Descriptions.Item label="工单版本">{detail.version}</Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Drawer>
    </main>
  );
}
