import { Alert, Space } from 'antd';
import type { ReactNode } from 'react';
import styles from './index.less';

/**
 * B 端业务页面统一头部辅助组件。
 *
 * 页面主标题与职责说明已上移到布局顶栏（按路由展示），本组件只收敛各页面头部
 * 其余结构（右侧操作/状态区），并统一呈现“接口契约待接入”提示。
 * 该组件只做展示编排，不承载具体业务页面实现，也不发起任何业务请求。
 */

export interface PageHeaderProps {
  /** 头部右侧操作或状态。 */
  extra?: ReactNode;
  /** 是否展示契约未接入状态。 */
  contractPending?: boolean;
}

/** B 端业务页面统一头部辅助，不承载具体业务页面实现。 */
export default function PageHeader({ extra, contractPending = false }: PageHeaderProps) {
  return (
    <>
      {extra && (
        <header className={styles.header}>
          <Space>{extra}</Space>
        </header>
      )}
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
