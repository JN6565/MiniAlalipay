import { Empty, Input, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

/**
 * 用户管理页面（P1）。
 *
 * B 端用户管理依赖 user-center（负责人闫泽华）提供的用户列表契约，OpenAPI 尚未定义该操作，
 * 因此本页仅为只读用户状态骨架，页面数据为空且不发起请求（系统分析 16.8 的 B 端身份边界）。
 * 正式契约落地并开放菜单入口前保持占位；登录名列展示脱敏值（loginNameMasked），禁止展示完整登录名。
 */

/** 用户列表展示行；登录名必须是脱敏字段。 */
interface UserRow {
  /** 用户编号。 */
  userId: string;
  /** 脱敏后的登录名。 */
  loginNameMasked: string;
  /** 昵称。 */
  nickname: string;
  /** 用户状态：ACTIVE/FROZEN/CLOSED。 */
  userStatus: string;
  /** 账户状态，与用户中心账户状态口径一致。 */
  accountStatus: string;
  /** 登录锁定截止时间，未锁定时为空。 */
  loginLockedUntil?: string;
  /** 创建时间。 */
  createdAt: string;
}

/** 用户列表列定义。 */
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
      <PageHeader extra={<Tag>P1</Tag>} contractPending />
      {/* 用户筛选：契约未接入前仅保留检索结构，不提交查询。 */}
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
