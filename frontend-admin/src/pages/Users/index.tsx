import { LeftOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import PageHeader from '@/components/PageHeader';
import {
  freezeAdminUser,
  listAdminUsers,
  unfreezeAdminUser,
  type AdminUserItem,
  type AdminUserStatus,
} from '@/services/users';
import pageStyles from '../page.less';

/** 用户状态中文标签（系统分析 2.3.26：DISABLED 即管理冻结）。 */
const STATUS_LABEL: Record<string, string> = {
  PROVISIONING: '注册中',
  ACTIVE: '正常',
  DISABLED: '已冻结',
};

/** 冻结理由占位提示。 */
const FREEZE_REASON_PLACEHOLDER = '请填写冻结理由（将作为审计记录）';

/**
 * 用户管理页面。
 *
 * 读取真实用户只读列表（user-center），系统管理员可冻结/解冻；仅 ACTIVE 可冻结、
 * 仅 DISABLED 可解冻，操作需二次确认并携带 CAS 版本。登录名仅展示服务端脱敏值，
 * 不展示手机号与任何密码类字段。
 */
export default function Users() {
  const { message } = App.useApp();
  const access = useAccess();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AdminUserStatus>();
  // 生效的登录名搜索词（进入 queryKey）；输入框临时值提交后才生效。
  const [loginName, setLoginName] = useState<string>();
  const [loginNameInput, setLoginNameInput] = useState('');
  const [action, setAction] = useState<{ user: AdminUserItem; kind: 'FREEZE' | 'UNFREEZE' }>();
  const [detail, setDetail] = useState<AdminUserItem>();
  const [form] = Form.useForm();
  // 服务端仅提供正向游标，使用游标栈回退上一页；空字符串表示第一页。
  const [cursor, setCursor] = useState<string>();
  const [cursorStack, setCursorStack] = useState<string[]>([]);

  const usersQuery = useQuery({
    queryKey: ['admin-users', status, loginName, cursor],
    queryFn: () => listAdminUsers(status, loginName, cursor),
  });

  const nextCursor = usersQuery.data?.data.nextCursor ?? null;
  const rows = usersQuery.data?.data.items ?? [];

  function changeStatus(value?: AdminUserStatus) {
    setStatus(value);
    setCursor(undefined);
    setCursorStack([]);
  }

  /** 提交登录名搜索：生效搜索词并回到第一页。 */
  function submitSearch() {
    setLoginName(loginNameInput.trim() || undefined);
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
      if (!target) throw new Error('缺少操作对象');
      if (target.kind === 'FREEZE') {
        const values = await form.validateFields();
        return freezeAdminUser(target.user.userId, target.user.version, values.reason);
      }
      return unfreezeAdminUser(target.user.userId, target.user.version);
    },
    onSuccess: () => {
      message.success(action?.kind === 'FREEZE' ? '用户已冻结' : '用户已解冻');
      setAction(undefined);
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '操作失败');
    },
  });

  /** 空值展示占位符。 */
  function placeholder(value: string | null | undefined): string {
    return value ? value : '—';
  }

  /** 冻结审计时间沿用 updatedAt（冻结/解冻都会刷新行更新时间）。 */
  const columns: ColumnsType<AdminUserItem> = [
    {
      title: '用户编号',
      dataIndex: 'userId',
      render: (value: string, record) => (
        <Button type="link" size="small" onClick={() => setDetail(record)}>
          {value}
        </Button>
      ),
    },
    { title: '登录名', dataIndex: 'loginNameMasked', render: (value: string) => <code>{value}</code> },
    { title: '昵称', dataIndex: 'nickname', render: (value: string) => placeholder(value) },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: AdminUserStatus) => (
        <Tag color={value === 'ACTIVE' ? 'green' : value === 'DISABLED' ? 'red' : 'default'}>
          {STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '登录锁定至',
      dataIndex: 'loginLockedUntil',
      render: (value: string | null) => placeholder(value),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        if (!access.canManageUsers) return null;
        if (record.status === 'ACTIVE') {
          return (
            <Button danger size="small" onClick={() => setAction({ user: record, kind: 'FREEZE' })}>
              冻结
            </Button>
          );
        }
        if (record.status === 'DISABLED') {
          return (
            <Button size="small" onClick={() => setAction({ user: record, kind: 'UNFREEZE' })}>
              解冻
            </Button>
          );
        }
        return null;
      },
    },
  ];

  return (
    <main className={pageStyles.page}>
      <PageHeader />
      <section className={pageStyles.toolbar} aria-label="用户筛选">
        <Select
          aria-label="用户状态"
          allowClear
          placeholder="用户状态"
          style={{ width: 160 }}
          options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          onChange={changeStatus}
        />
        <Input
          aria-label="登录名搜索"
          allowClear
          prefix={<SearchOutlined />}
          placeholder="按登录名搜索"
          style={{ width: 220 }}
          value={loginNameInput}
          onChange={(e) => setLoginNameInput(e.target.value)}
          onPressEnter={submitSearch}
        />
        <Button type="primary" className="admin-btn-query" onClick={submitSearch}>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="用户列表">
        <Table<AdminUserItem>
          rowKey="userId"
          columns={columns}
          dataSource={rows}
          loading={usersQuery.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                description={usersQuery.isError
                  ? '加载失败，请确认网关已启动'
                  : loginName
                    ? '未查询到匹配该登录名的用户，请调整搜索词'
                    : status
                      ? '暂无该状态用户'
                      : '暂无用户数据'}
              />
            ),
          }}
          scroll={{ x: 760 }}
        />
        {/* 游标分页置于表格右下角，交互与交易查询页保持一致。 */}
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
        title={action
          ? action.kind === 'FREEZE'
            ? '确认冻结用户（需二次确认）'
            : '确认解冻用户（需二次确认）'
          : ''}
        open={!!action}
        confirmLoading={mutation.isPending}
        onOk={() => mutation.mutate()}
        onCancel={() => setAction(undefined)}
        destroyOnClose
      >
        {action?.kind === 'FREEZE' && (
          <Form form={form} layout="vertical" initialValues={{ reason: '' }}>
            <Form.Item
              name="reason"
              label="冻结理由"
              rules={[{ required: true, whitespace: true, message: '请输入冻结理由' }]}
            >
              <Input.TextArea rows={3} maxLength={200} placeholder={FREEZE_REASON_PLACEHOLDER} />
            </Form.Item>
          </Form>
        )}
        {action?.kind === 'UNFREEZE' && (
          <p>
            确认解冻用户 <code>{action.user.loginNameMasked}</code>？解冻后该用户可重新登录。
          </p>
        )}
      </Modal>
      <Drawer
        title={detail ? `用户详情（${STATUS_LABEL[detail.status] ?? detail.status}）` : ''}
        open={!!detail}
        width={560}
        onClose={() => setDetail(undefined)}
      >
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="用户编号">
              <Typography.Text copyable>{detail.userId}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="登录名">{detail.loginNameMasked}</Descriptions.Item>
            <Descriptions.Item label="昵称">{placeholder(detail.nickname)}</Descriptions.Item>
            <Descriptions.Item label="状态">{STATUS_LABEL[detail.status] ?? detail.status}</Descriptions.Item>
            <Descriptions.Item label="登录锁定至">{placeholder(detail.loginLockedUntil)}</Descriptions.Item>
            <Descriptions.Item label="冻结操作者">{placeholder(detail.disabledBy)}</Descriptions.Item>
            <Descriptions.Item label="冻结理由">{placeholder(detail.disabledReason)}</Descriptions.Item>
            <Descriptions.Item label="版本">{detail.version}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{detail.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </main>
  );
}
