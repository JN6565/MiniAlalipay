import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Button, Result, Card, SpinLoading } from 'antd-mobile';
import { CheckCircleFill, CloseCircleFill } from 'antd-mobile-icons';
import * as collectionService from '@/services/collection';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
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

  return (
    <div className="collection-result-page">
      <Result
        icon={isSuccess ? <CheckCircleFill /> : isProcessing ? <SpinLoading /> : <CloseCircleFill />}
        status={isSuccess ? 'success' : isProcessing ? 'waiting' : 'error'}
        title={getStatusText()}
        description={getStatusDescription()}
      />

      <Card className="result-card">
        <div className="result-row">
          <span className="result-label">交易号</span>
          <span className="result-value">{order?.transactionId || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">付款人</span>
          <span className="result-value">{order?.payerName || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">收款人</span>
          <span className="result-value">{order?.payeeName || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">金额</span>
          <span className="result-value">
            {order?.amountFen != null ? <AmountDisplay amountFen={order.amountFen} /> : '-'}
          </span>
        </div>
        <div className="result-row">
          <span className="result-label">备注</span>
          <span className="result-value">{order?.subject || '-'}</span>
        </div>
      </Card>

      <Button block color="primary" onClick={() => history.push('/h5/home')}>
        返回首页
      </Button>
    </div>
  );
};

export default CollectionResultPage;
