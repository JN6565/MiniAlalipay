import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { SpinLoading } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import { formatBalance } from '@/services/bankCard';
import { IconSet, type IconName } from '@/components/h5/common';
import './index.less';

/** 资金终态只能由统一交易终态发布器回填，受理后的中间态需要轮询等待。 */
const NON_TERMINAL_STATUSES = ['DRAFT', 'PENDING_CONFIRMATION', 'RISK_REVIEW', 'PROCESSING'];

const CollectionResultPage: React.FC = () => {
  // 路由参数是订单 ID（结果页按订单查询状态），不是交易 ID
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [order, setOrder] = useState<collectionService.CollectionOrder | null>(null);

  useEffect(() => {
    document.title = '支付结果';
    if (!id) return;
    // C2C 支付受理后资金事实异步回填，初次状态通常为 PROCESSING；
    // 每 2 秒轮询一次，最多 15 次直到终态，避免用户手动刷新。
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;

    const load = async () => {
      try {
        const data = await collectionService.getOrderStatus(String(id));
        if (cancelled) return;
        setOrder(data);
        if (NON_TERMINAL_STATUSES.includes(data.status) && attempts < 15) {
          attempts += 1;
          timer = setTimeout(load, 2000);
          return;
        }
      } catch (error) {
        console.error('加载支付结果失败', error);
      }
      if (!cancelled) setLoading(false);
    };

    load();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [id]);

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  const isSuccess = order?.status === 'SUCCESS';
  const isProcessing = order
    ? NON_TERMINAL_STATUSES.includes(order.status)
    : false;

  // 三态统一版式：success 绿 / fail 红 / processing 橙
  const statusCfg = isSuccess
    ? { color: 'var(--h5-success)', icon: 'check' as IconName }
    : isProcessing || order?.status === 'MANUAL_REVIEW'
      ? { color: 'var(--h5-warning)', icon: 'clock' as IconName }
      : { color: 'var(--h5-amount-in)', icon: 'close' as IconName };

  const getStatusText = () => {
    if (isSuccess) return '支付成功';
    if (order?.status === 'FAILED') return '支付失败';
    if (order?.status === 'CANCELLED') return '支付已取消';
    if (order?.status === 'EXPIRED') return '订单已过期';
    if (order?.status === 'MANUAL_REVIEW') return '人工审核中';
    if (isProcessing) return '支付处理中';
    return '支付状态未知';
  };

  const getStatusDescription = () => {
    if (isSuccess) return '资金已到账';
    if (order?.status === 'FAILED') return '资金已退回';
    if (order?.status === 'CANCELLED') return '支付已取消';
    if (order?.status === 'EXPIRED') return '订单超过有效期';
    if (order?.status === 'MANUAL_REVIEW') return '请等待审核结果';
    if (isProcessing) return '请稍后查看结果';
    return '请联系客服查询';
  };

  const detailRows: Array<[string, string]> = [
    ['交易号', order?.transactionId || '-'],
    ['付款人', order?.payerName || '-'],
    ['收款人', order?.payeeName || '-'],
    ['备注', order?.subject || '-'],
  ];

  return (
    <div className="collection-result-page">
      {/* 状态区：圆形状态图标 + 标题 + 金额 + 说明 + 双入口 */}
      <div className="result-status">
        <div className="status-icon" style={{ background: statusCfg.color }}>
          <IconSet name={statusCfg.icon} size={26} width={2.4} color="#fff" />
        </div>
        <div className="status-title">{getStatusText()}</div>
        {order?.amountFen != null && (
          <div className="status-amount">¥{formatBalance(order.amountFen)}</div>
        )}
        <div className="status-desc">{getStatusDescription()}</div>
        <div className="status-actions">
          <div className="result-btn outline" onClick={() => history.push('/h5/home')}>
            返回首页
          </div>
          <div
            className="h5-btn-gradient result-btn"
            onClick={() => history.push('/h5/account/transactions')}
          >
            查看账单
          </div>
        </div>
      </div>

      {/* 交易凭证字段 */}
      <div className="result-receipt">
        <div className="receipt-divider" />
        {detailRows.map(([label, value]) => (
          <div className="receipt-row" key={label}>
            <span className="receipt-label">{label}</span>
            <span className="receipt-value">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default CollectionResultPage;
