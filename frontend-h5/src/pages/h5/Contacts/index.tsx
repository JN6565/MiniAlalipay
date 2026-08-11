import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as userService from '@/services/user';
import { useTabActiveRefresh } from '@/utils/useTabActiveRefresh';
import { IconSet, EmptyState, Skeleton, BuiltinAvatar, AVATAR_KINDS, AvatarKind } from '@/components/h5/common';
import './index.less';

/** 按昵称稳定取一个内置头像种类（无真实头像数据，哈希取模保证同人同头像）。 */
function avatarKindOf(name?: string): AvatarKind {
  let h = 0;
  for (const ch of name || '') h = (h * 31 + ch.charCodeAt(0)) % 997;
  return AVATAR_KINDS[h % AVATAR_KINDS.length];
}

/** 首字母分组键：英文取大写字母，其余归入 #。 */
function groupKeyOf(name?: string): string {
  const ch = (name || '').charAt(0).toUpperCase();
  return /^[A-Z]$/.test(ch) ? ch : '#';
}

const ContactsPage: React.FC = () => {
  const [friends, setFriends] = useState<userService.Friend[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<userService.PayeeInfo[]>([]);
  const [searching, setSearching] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    loadFriends();
    loadPendingCount();
  }, []);

  // 联系人页保活常驻，好友请求在二级页接受/对方同意后，
  // 回切本页时必须静默重拉好友列表与待处理角标，否则展示的是旧数据。
  useTabActiveRefresh('/h5/contacts', () => {
    loadFriends();
    loadPendingCount();
  });

  const loadFriends = async () => {
    setLoading(true);
    try {
      const res = (await userService.getFriends()) as unknown as userService.Friend[];
      setFriends(res || []);
    } catch (e: any) {
      Toast.show(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const loadPendingCount = async () => {
    try {
      const res = (await userService.getPendingFriendRequests()) as unknown as userService.FriendRequest[];
      setPendingCount((res || []).length);
    } catch {
      // ignore
    }
  };

  const doSearch = useCallback(async (keyword: string) => {
    if (!keyword.trim()) {
      setSearchResults([]);
      return;
    }
    setSearching(true);
    try {
      const res = (await userService.searchUsers(keyword.trim(), 10)) as unknown as userService.PayeeInfo[];
      setSearchResults(res || []);
    } catch (e: any) {
      console.warn('搜索失败:', e.message);
    } finally {
      setSearching(false);
    }
  }, []);

  const handleSearch = (val: string) => {
    setSearchKeyword(val);
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (!val.trim()) {
      setSearchResults([]);
      return;
    }
    searchTimerRef.current = setTimeout(() => doSearch(val), 500);
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

  // 好友按首字母分组（A-Z 优先，# 收尾），组内保持后端返回顺序
  const friendGroups = useMemo(() => {
    const map = new Map<string, userService.Friend[]>();
    friends.forEach((f) => {
      const key = groupKeyOf(f.friendName);
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(f);
    });
    return Array.from(map.entries()).sort(([a], [b]) => {
      if (a === '#') return 1;
      if (b === '#') return -1;
      return a.localeCompare(b);
    });
  }, [friends]);

  const showSearchSection = searchKeyword.trim().length > 0;

  return (
    <div className="contacts-page">
      {/* 柔渐变头部带：搜索卡悬浮其上 */}
      <div className="contacts-head" />
      <div className="contacts-body">
        <div className="contacts-card">
          {/* 搜索框：搜索手机号添加好友 */}
          <div className="contacts-search">
            <IconSet name="search" size={14} color="#94a3ba" />
            <input
              className="contacts-search-input"
              placeholder="搜索手机号添加好友"
              value={searchKeyword}
              onChange={(e) => handleSearch(e.target.value)}
            />
            {searchKeyword && (
              <span className="contacts-search-clear" onClick={() => handleSearch('')}>
                <IconSet name="close" size={12} />
              </span>
            )}
          </div>

          {/* 搜索结果区 */}
          {showSearchSection && (
            <div className="contacts-section">
              <div className="contacts-section-label">搜索结果</div>
              {searching ? (
                <Skeleton rows={2} />
              ) : searchResults.length === 0 ? (
                <div className="contacts-search-empty">未找到相关用户</div>
              ) : (
                searchResults.map((user) => {
                  const isFriend = friends.some((f) => f.friendUserId === user.userId);
                  return (
                    <div key={user.userId} className="contact-row">
                      <BuiltinAvatar kind={avatarKindOf(user.nickname)} size={40} />
                      <div className="contact-meta">
                        <div className="contact-name">{user.nickname}</div>
                        <div className="contact-sub">{user.maskedPhone || user.accountNumber}</div>
                      </div>
                      {isFriend ? (
                        <span className="contact-pill disabled">已是好友</span>
                      ) : (
                        <span className="contact-pill" onClick={() => handleSendRequest(user.userId)}>
                          添加好友
                        </span>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          )}

          {/* 新朋友入口：待处理好友请求角标 */}
          {!showSearchSection && (
            <div className="contacts-section">
              <div className="contact-row new-friend-row" onClick={() => history.push('/h5/friend-requests')}>
                <span className="new-friend-icon">
                  <IconSet name="plus" size={16} color="#256cff" />
                </span>
                <div className="contact-meta">
                  <div className="contact-name">新朋友</div>
                  <div className="contact-sub">查看好友请求</div>
                </div>
                {pendingCount > 0 && <span className="new-friend-badge">{pendingCount}</span>}
                <IconSet name="chevronRight" size={14} color="#94a3ba" />
              </div>
            </div>
          )}

          {/* 好友列表：按首字母分组 */}
          {!showSearchSection && (
            <div className="contacts-section">
              <div className="contacts-section-label">联系人 ({friends.length})</div>
              {loading ? (
                <Skeleton rows={4} />
              ) : friends.length === 0 ? (
                <EmptyState
                  icon={<IconSet name="contacts" size={30} color="#94a3ba" />}
                  text="暂无联系人"
                  hint="搜索手机号添加好友"
                />
              ) : (
                friendGroups.map(([letter, list]) => (
                  <div key={letter} className="contact-group">
                    <div className="contact-group-label">{letter}</div>
                    {list.map((friend) => (
                      <div key={friend.friendUserId} className="contact-row">
                        <BuiltinAvatar kind={avatarKindOf(friend.friendName)} size={40} />
                        <div className="contact-meta">
                          <div className="contact-name">
                            {friend.friendName}
                            {friend.alias ? <span className="contact-alias">({friend.alias})</span> : null}
                          </div>
                          <div className="contact-sub">{friend.maskedPhone || friend.accountNumber}</div>
                        </div>
                        <span className="contact-pill" onClick={() => handleTransfer(friend)}>
                          转账
                        </span>
                      </div>
                    ))}
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ContactsPage;
