import React, { useEffect, useMemo, useState } from 'react';
import { useParams, history, useLocation } from 'umi';
import { Button, Toast, SpinLoading } from 'antd-mobile';
import { QRCodeSVG } from 'qrcode.react';
import * as collectionService from '@/services/collection';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { REQUEST_STATUS_TEXT } from '@/constants';
import { formatCountdown } from '@/utils/format';
import './index.less';

const CollectionRequestPage: React.FC = () => {
  const { id } = useParams();
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [request, setRequest] = useState<collectionService.CollectionRequest | null>(null);
  const [countdown, setCountdown] = useState(0);
  // 一码多收：同一请求可能有多笔来自不同付款人的订单，页面存活期间轮询展示
  const [orders, setOrders] = useState<collectionService.RequestOrderItem[]>([]);

  // 收款令牌仅在创建响应中返回一次：优先导航 state，降级 sessionStorage，两者皆无视为失效
  const collectionUrl = useMemo(() => {
    const navState = (location as any).state as { collectionUrl?: string } | undefined;
    if (navState?.collectionUrl) return navState.collectionUrl;
    if (id) return sessionStorage.getItem(`collection-qr-${id}`) || '';
    return '';
  }, [id, location]);

  const qrValue = useMemo(() => {
    if (!collectionUrl) return '';
    if (collectionUrl.startsWith('/')) return `${window.location.origin}${collectionUrl}`;
    return collectionUrl;
  }, [collectionUrl]);

  useEffect(() => {
    if (id) {
      loadRequest(id);
    }
  }, [id]);

  useEffect(() => {
    // 收款记录每 3 秒轮询一次，收款方实时看到新付款人与支付结果；页面卸载时清理
    if (!id) return undefined;
    let cancelled = false;
    const loadOrders = () => {
      collectionService.getRequestOrders(id)
        .then((data) => { if (!cancelled) setOrders(data || []); })
        .catch(() => { /* 轮询失败不阻断页面，下一轮重试 */ });
    };
    loadOrders();
    const timer = setInterval(loadOrders, 3000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [id]);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const loadRequest = async (requestId: string) => {
    try {
      const data = await collectionService.getRequestStatus(requestId);
      setRequest(data);

      // 计算倒计时
      const expiresAt = new Date(data.expiresAt).getTime();
      const now = Date.now();
      const remaining = Math.max(0, Math.floor((expiresAt - now) / 1000));
      setCountdown(remaining);
    } catch (error) {
      console.error('加载失败', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!request) return;

    try {
      await collectionService.cancelRequest(request.requestId, request.version);
      Toast.show({ icon: 'success', content: '已取消收款' });
      loadRequest(request.requestId);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '取消失败' });
    }
  };

  const handleCopyLink = () => {
    if (!qrValue) return;
    navigator.clipboard
      .writeText(qrValue)
      .then(() => Toast.show({ icon: 'success', content: '链接已复制' }))
      .catch(() => Toast.show({ icon: 'fail', content: '复制失败' }));
  };

  // 订单状态到收款记录展示文案的映射；中间态统一归入待支付/支付中
  const ORDER_STATUS_TEXT: Record<string, string> = {
    DRAFT: '待支付',
    PENDING_CONFIRMATION: '待支付',
    PROCESSING: '支付中',
    RISK_REVIEW: '审核中',
    MANUAL_REVIEW: '审核中',
    SUCCESS: '支付成功',
    FAILED: '支付失败',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
  };

  const formatOrderTime = (value?: string) => {
    if (!value) return '';
    const date = new Date(value);
    const pad = (part: number) => String(part).padStart(2, '0');
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  if (!request) {
    return <div className="error-state">请求不存在</div>;
  }

  return (
    <div className="collection-request-page">
      <div className="qr-header">
        <span className="request-status">
          {REQUEST_STATUS_TEXT[request.status] || request.status}
        </span>
        {request.status === 'OPEN' && countdown > 0 && (
          <span className="request-countdown">剩余时间：{formatCountdown(countdown)}</span>
        )}
      </div>

      {!collectionUrl ? (
        <div className="qr-empty">
          <div className="qr-empty-text">收款码信息已失效，请返回重新设置金额</div>
          <Button block color="primary" onClick={() => history.push('/h5/collection')}>
            返回收款
          </Button>
        </div>
      ) : (
        <div className="qr-main">
          <div className="qr-frame">
            <QRCodeSVG value={qrValue} size={240} level="H" includeMargin={true} />
          </div>

          <div className="qr-amount">
            <div className="qr-amount-label">收款金额</div>
            <AmountDisplay amountFen={request.amountFen} size="large" />
          </div>

          {request.subject && <div className="qr-subject">备注：{request.subject}</div>}

          <div className="request-actions">
            {request.status === 'OPEN' ? (
              <>
                <Button block color="primary" onClick={handleCopyLink}>
                  复制链接
                </Button>
                <Button block onClick={handleCancel}>
                  取消收款
                </Button>
              </>
            ) : (
              <Button block color="primary" onClick={() => history.push('/h5/home')}>
                返回首页
              </Button>
            )}
          </div>
        </div>
      )}

      {/* 一码多收收款记录：展示每笔订单的脱敏付款人、金额、备注、状态与创建时间 */}
      {orders.length > 0 && (
        <div className="order-records">
          <div className="order-records-title">收款记录（{orders.length} 笔）</div>
          {orders.map((order) => (
            <div className="order-record-item" key={order.collectionOrderId}>
              <div className="order-record-main">
                <span className="order-record-payer">{order.payerName || '付款人'}</span>
                <span className="order-record-status">{ORDER_STATUS_TEXT[order.status] || order.status}</span>
              </div>
              <div className="order-record-sub">
                <span>{order.subject || '无备注'}</span>
                <span>{formatOrderTime(order.createdAt)}</span>
              </div>
              <div className="order-record-amount">
                <AmountDisplay amountFen={order.amountFen || 0} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default CollectionRequestPage;
