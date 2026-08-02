import { SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Timeline, Typography } from 'antd';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

export default function Trace() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="链路追溯"
        description="按交易编号或链路编号定位 Agent、网关、风控、事务和账本阶段。"
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="链路检索">
        <Input
          className={pageStyles.searchInput}
          aria-label="交易编号或链路编号"
          placeholder="输入交易编号或链路编号"
          prefix={<SearchOutlined />}
        />
        <Button type="primary" disabled>
          查询链路
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="链路时间线">
        <Typography.Title level={4} className={pageStyles.panelTitle}>
          脱敏 Span 时间线
        </Typography.Title>
        <Timeline items={[]} pending={false} />
        <Empty description="链路接口接入后按时间展示各服务 Span" />
      </section>
    </main>
  );
}
