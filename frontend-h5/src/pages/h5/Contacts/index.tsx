import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Button, Toast, Empty, SearchBar } from 'antd-mobile';
import * as userService from '@/services/user';
import './index.less';

const ContactsPage: React.FC = () => {
  const [friends, setFriends] = useState<userService.Friend[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<userService.PayeeInfo[]>([]);
  const [searching, setSearching] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);

  useEffect(() => {
    loadFriends();
    loadPendingCount();
  }, []);

  const loadFriends = async () => {
    try {
      const res = await userService.getFriends();
      setFriends(res || []);
    } catch (e: any) {
      Toast.show(e.message || '加载失败');
    }
  };

  const loadPendingCount = async () => {
    try {
      const res = await userService.getPendingFriendRequests();
      setPendingCount((res || []).length);
    } catch {
      // ignore
    }
  };

  const handleSearch = async (val: string) => {
    setSearchKeyword(val);
    if (!val.trim()) {
      setSearchResults([]);
      return;
    }
    setSearching(true);
    try {
      const res = await userService.searchUsers(val.trim(), 10);
      setSearchResults(res || []);
    } catch (e: any) {
      Toast.show(e.message || '搜索失败');
    } finally {
      setSearching(false);
    }
  };

  const handleSendRequest = async (userId: string) => {
    try {
      await userService.sendFriendRequest(userId);
      Toast.show({ content: '好友请求已发送', icon: 'success' });
      setSearchKeyword('');
      setSearchResults([]);
    } catch (e: any) {
      Toast.show(e.message || '发送失败');
    }
  };

  const handleTransfer = (friend: userService.Friend) => {
    const params = new URLSearchParams({
      payeeUserId: friend.friendUserId,
      payeeName: friend.friendName,
      accountNumber: friend.accountNumber,
    });
    history.push(`/h5/transfer?${params.toString()}`);
  };

  return (
    <div className="contacts-page">
      {/* 搜索栏 */}
      <div className="search-section">
        <SearchBar
          placeholder="搜索手机号添加好友"
          value={searchKeyword}
          onChange={handleSearch}
        />
      </div>

      {/* 搜索结果 */}
      {searchResults.length > 0 && (
        <div className="section-card">
          <div className="section-title">搜索结果</div>
          {searchResults.map((user) => {
            const isFriend = friends.some((f) => f.friendUserId === user.userId);
            return (
              <div key={user.userId} className="search-item">
                <div className="user-info">
                  <div className="avatar-circle">{user.nickname?.charAt(0) || '?'}</div>
                  <div className="user-meta">
                    <div className="user-name">{user.nickname}</div>
                    <div className="user-phone">{user.accountNumber}</div>
                  </div>
                </div>
                {isFriend ? (
                  <Button size="small" fill="outline" disabled>
                    已是好友
                  </Button>
                ) : (
                  <Button size="small" color="primary" onClick={() => handleSendRequest(user.userId)}>
                    添加好友
                  </Button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 好友请求入口 */}
      <div className="request-entry" onClick={() => history.push('/h5/friend-requests')}>
        <div className="request-left">
          <span className="request-icon">📬</span>
          <span>新朋友</span>
        </div>
        {pendingCount > 0 && <span className="badge">{pendingCount}</span>}
      </div>

      {/* 好友列表 */}
      <div className="section-card">
        <div className="section-title">联系人 ({friends.length})</div>
        {friends.length === 0 ? (
          <Empty description="暂无联系人，搜索手机号添加好友" style={{ padding: '40px 0' }} />
        ) : (
          friends.map((friend) => (
            <div key={friend.friendUserId} className="friend-item">
              <div className="user-info">
                <div className="avatar-circle">{friend.friendName?.charAt(0) || '?'}</div>
                <div className="user-meta">
                  <div className="user-name">
                    {friend.friendName}
                    {friend.alias ? <span className="alias-tag">({friend.alias})</span> : null}
                  </div>
                  <div className="user-phone">{friend.accountNumber}</div>
                </div>
              </div>
              <Button size="small" color="primary" onClick={() => handleTransfer(friend)}>
                转账
              </Button>
            </div>
          ))
        )}
      </div>

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab" onClick={() => history.push('/h5/home')}>
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/ai-talk')}>
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab on">
          <span className="tab-icon">👥</span>
          <span className="tab-label">联系人</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/profile')}>
          <span className="tab-icon">👤</span>
          <span className="tab-label">我的</span>
        </div>
      </div>
    </div>
  );
};

export default ContactsPage;
