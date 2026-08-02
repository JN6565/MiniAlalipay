import { Empty, Input, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

interface UserRow {
  userId: string;
  loginNameMasked: string;
  nickname: string;
  userStatus: string;
  accountStatus: string;
  loginLockedUntil?: string;
  createdAt: string;
}

const columns: ColumnsType<UserRow> = [
  { title: '用户编号', dataIndex: 'userId', key: 'userId' },
  { title: '登录名', dataIndex: 'loginNameMasked', key: 'loginNameMasked' },
  { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
  { title: '用户状态', dataIndex: 'userStatus', key: 'userStatus' },
  { title: '账户状态', dataIndex: 'accountStatus', key: 'accountStatus' },
  { title: '登录锁定至', dataIndex: 'loginLockedUntil', key: 'loginLockedUntil' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
];

export default function Users() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="用户管理"
        description="P1 只读用户状态页面；后端和 OpenAPI 尚未给出可编码契约。"
        extra={<Tag>P1</Tag>}
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="用户筛选">
        <Input aria-label="用户检索" placeholder="用户编号、脱敏登录名或昵称" style={{ width: 320 }} />
        <Select
          aria-label="用户状态"
          allowClear
          placeholder="用户状态"
          style={{ width: 160 }}
          options={[
            { value: 'ACTIVE', label: '正常' },
            { value: 'FROZEN', label: '已冻结' },
            { value: 'CLOSED', label: '已关闭' },
          ]}
        />
      </section>
      <section className={pageStyles.panel} aria-label="用户列表">
        <Table<UserRow>
          rowKey="userId"
          columns={columns}
          dataSource={[]}
          pagination={false}
          locale={{ emptyText: <Empty description="正式用户管理契约落地后开放菜单入口" /> }}
          scroll={{ x: 880 }}
        />
      </section>
    </main>
  );
}
