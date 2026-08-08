import { SpinLoading } from 'antd-mobile';
import React, { useEffect, useState } from 'react';
import { SLOW_NETWORK_MESSAGE, slowRequestTracker } from '@/services/networkFeedback';
import styles from './index.less';

/** 全局弱网提示条：慢请求期间提醒用户继续等待，不遮挡页面操作。 */
export default function NetworkStatusNotice() {
  const [visible, setVisible] = useState(false);

  useEffect(() => slowRequestTracker.subscribe(setVisible), []);

  if (!visible) return null;

  return (
    <div className={styles.notice} role="status" aria-live="polite">
      <SpinLoading color="currentColor" style={{ '--size': '16px' }} />
      <span>{SLOW_NETWORK_MESSAGE}</span>
    </div>
  );
}
