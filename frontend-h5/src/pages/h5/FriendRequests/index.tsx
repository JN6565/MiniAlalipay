import React, { useEffect, useState } from 'react';
import { Toast } from 'antd-mobile';
import * as userService from '@/services/user';
import { IconSet, EmptyState, Skeleton, BuiltinAvatar, AVATAR_KINDS, AvatarKind } from '@/components/h5/common';
import './index.less';

/** 按昵称稳定取一个内置头像种类（无真实头像数据，哈希取模保证同人同头像）。 */
function avatarKindOf(name?: string): AvatarKind {
  let h = 0;
  for (const ch of name || '') h = (h * 31 + ch.charCodeAt(0)) % 997;
  return AVATAR_KINDS[h % AVATAR_KINDS.length];
}

const FriendRequests: React.FC = () => {
  const [requests, setRequests] = useState<userService.FriendRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRequests();
  }, []);

  const loadRequests = async () => {
    setLoading(true);
    try {
      const res = (await userService.getPendingFriendRequests()) as unknown as userService.FriendRequest[];
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
      <div className="fr-head" />
      <div className="fr-body">
        <div className="fr-card">
          {loading ? (
            <Skeleton rows={3} />
          ) : requests.length === 0 ? (
            <EmptyState
              icon={<IconSet name="contacts" size={30} color="#94a3ba" />}
              text="暂无好友请求"
              hint="接受请求后即可互相转账"
            />
          ) : (
            requests.map((req) => (
              <div key={req.requestId} className="fr-row">
                <BuiltinAvatar kind={avatarKindOf(req.fromUserName)} size={40} />
                <div className="fr-meta">
                  <div className="fr-name">{req.fromUserName}</div>
                  <div className="fr-message">{req.message || '请求添加你为好友'}</div>
                </div>
                <div className="fr-actions">
                  {req.status === 'PENDING' ? (
                    <>
                      <span className="fr-accept" onClick={() => handleAccept(req.requestId)}>
                        接受
                      </span>
                      <span className="fr-reject" onClick={() => handleReject(req.requestId)}>
                        拒绝
                      </span>
                    </>
                  ) : (
                    <span className="fr-done">
                      {req.status === 'ACCEPTED' ? '已添加' : '已拒绝'}
                    </span>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default FriendRequests;
