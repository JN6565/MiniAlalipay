import { Alert, Space, Typography } from 'antd';
import type { ReactNode } from 'react';
import styles from './index.less';

export interface PageHeaderProps {
  /** 页面主标题。 */
  title: string;
  /** 页面职责的简短说明。 */
  description: string;
  /** 标题区右侧操作或状态。 */
  extra?: ReactNode;
  /** 是否展示契约未接入状态。 */
  contractPending?: boolean;
}

/** B 端业务页面统一标题区，不承载具体业务页面实现。 */
export default function PageHeader({
  title,
  description,
  extra,
  contractPending = false,
}: PageHeaderProps) {
  return (
    <>
      <header className={styles.header}>
        <div>
          <Typography.Title level={2}>{title}</Typography.Title>
          <Typography.Text type="secondary">{description}</Typography.Text>
        </div>
        {extra && <Space>{extra}</Space>}
      </header>
      {contractPending && (
        <Alert
          className={styles.alert}
          type="info"
          showIcon
          message="接口契约待接入"
          description="当前保留页面结构与字段口径，不发送业务请求。"
        />
      )}
    </>
  );
}
