import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Card, Button, Toast, SpinLoading } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { formatTime, formatCountdown } from '@/utils/format';
import './index.less';

const CollectionRequestPage: React.FC = () => {
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [request, setRequest] = useState<collectionService.CollectionRequest | null>(null);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (id) {
      loadRequest(id);
    }
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
      await collectionService.cancelRequest(request.requestId);
      Toast.show({ icon: 'success', content: '已取消' });
      loadRequest(request.requestId);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '取消失败' });
    }
  };

  const handleShare = () => {
    if (request?.shareUrl) {
      navigator.clipboard.writeText(request.shareUrl);
      Toast.show({ icon: 'success', content: '链接已复制' });
    }
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
      <Card className="request-card">
        <div className="request-header">
          <div className="request-status">
            状态：{request.status === 'OPEN' ? '进行中' : request.status}
          </div>
          {request.status === 'OPEN' && countdown > 0 && (
            <div className="request-countdown">
              剩余时间：{formatCountdown(countdown)}
            </div>
          )}
        </div>

        <div className="request-amount">
          <AmountDisplay amountFen={request.amountFen} size="large" />
        </div>

        {request.subject && (
          <div className="request-subject">备注：{request.subject}</div>
        )}

        <div className="request-info">
          <div className="info-row">
            <span className="info-label">创建时间</span>
            <span className="info-value">{formatTime(request.createdAt)}</span>
          </div>
          <div className="info-row">
            <span className="info-label">过期时间</span>
            <span className="info-value">{formatTime(request.expiresAt)}</span>
          </div>
        </div>
      </Card>

      {request.status === 'OPEN' && (
        <div className="request-actions">
          <Button block color="primary" onClick={handleShare}>
            分享链接
          </Button>
          <Button block onClick={handleCancel}>
            取消请求
          </Button>
        </div>
      )}

      {request.status === 'SUCCESS' && (
        <Button block color="primary" onClick={() => history.push('/h5/home')}>
          返回首页
        </Button>
      )}
    </div>
  );
};

export default CollectionRequestPage;
