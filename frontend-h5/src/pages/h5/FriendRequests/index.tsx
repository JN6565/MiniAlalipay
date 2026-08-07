import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Button, Toast, Empty } from 'antd-mobile';
import * as userService from '@/services/user';
import './index.less';

const FriendRequests: React.FC = () => {
  const [requests, setRequests] = useState<userService.FriendRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRequests();
  }, []);

  const loadRequests = async () => {
    setLoading(true);
    try {
      const res = await userService.getPendingFriendRequests();
      setRequests(res || []);
    } catch (e: any) {
      Toast.show(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAccept = async (requestId: string) => {
    try {
      await userService.acceptFriendRequest(requestId);
      Toast.show({ content: '已接受', icon: 'success' });
      setRequests((prev) => prev.filter((r) => r.requestId !== requestId));
    } catch (e: any) {
      Toast.show(e.message || '操作失败');
    }
  };

  const handleReject = async (requestId: string) => {
    try {
      await userService.rejectFriendRequest(requestId);
      Toast.show({ content: '已拒绝' });
      setRequests((prev) => prev.filter((r) => r.requestId !== requestId));
    } catch (e: any) {
      Toast.show(e.message || '操作失败');
    }
  };

  return (
    <div className="friend-requests-page">
      <div className="requests-content">
        {loading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : requests.length === 0 ? (
          <Empty description="暂无好友请求" style={{ padding: '60px 0' }} />
        ) : (
          requests.map((req) => (
            <div key={req.requestId} className="request-item">
              <div className="request-info">
                <div className="avatar-circle">{req.fromUserName?.charAt(0) || '?'}</div>
                <div className="request-meta">
                  <div className="request-name">{req.fromUserName}</div>
                  {req.message && <div className="request-message">{req.message}</div>}
                </div>
              </div>
              <div className="request-actions">
                {req.status === 'PENDING' ? (
                  <>
                    <Button size="small" color="primary" onClick={() => handleAccept(req.requestId)}>
                      接受
                    </Button>
                    <Button size="small" fill="outline" onClick={() => handleReject(req.requestId)}>
                      拒绝
                    </Button>
                  </>
                ) : (
                  <span className="status-text">
                    {req.status === 'ACCEPTED' ? '已接受' : '已拒绝'}
                  </span>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default FriendRequests;
