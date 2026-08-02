import { SearchOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Input, Typography } from 'antd';
import PageHeader from '@/components/PageHeader';
import pageStyles from '../page.less';

export default function Transactions() {
  return (
    <main className={pageStyles.page}>
      <PageHeader
        title="交易查询与回执"
        description="按交易号查询服务端唯一事实状态和脱敏电子回执。"
        contractPending
      />
      <section className={pageStyles.toolbar} aria-label="交易检索">
        <Input
          className={pageStyles.searchInput}
          aria-label="交易编号"
          placeholder="输入交易编号"
          prefix={<SearchOutlined />}
        />
        <Button type="primary" disabled>
          查询
        </Button>
      </section>
      <section className={pageStyles.panel} aria-label="交易详情">
        <Typography.Title level={4} className={pageStyles.panelTitle}>
          交易唯一事实状态
        </Typography.Title>
        <Descriptions
          bordered
          column={{ xs: 1, sm: 2, lg: 3 }}
          items={[
            { key: 'transactionId', label: '交易编号', children: '-' },
            { key: 'status', label: '交易状态', children: '-' },
            { key: 'amountFen', label: '金额', children: '-' },
            { key: 'businessType', label: '业务类型', children: '-' },
            { key: 'sourceType', label: '来源类型', children: '-' },
            { key: 'fundingSource', label: '资金来源', children: '-' },
            { key: 'occurredAt', label: '发生时间', children: '-' },
            { key: 'payer', label: '付款方', children: '-' },
            { key: 'payee', label: '收款方', children: '-' },
            { key: 'traceId', label: '链路编号', children: '-' },
          ]}
        />
        <Empty description="输入交易编号后展示脱敏交易详情" />
      </section>
    </main>
  );
}
