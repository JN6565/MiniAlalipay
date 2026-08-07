import React, { useMemo } from 'react';
import { Popup } from 'antd-mobile';
import dayjs from 'dayjs';
import type { SessionInfo } from '../types';

interface Props {
  visible: boolean;
  onClose: () => void;
  sessions: SessionInfo[];
  currentSessionId: string | null;
  loading: boolean;
  onSelect: (sessionId: string) => void;
  onNewSession: () => void;
}

/** 会话分组键 */
type GroupKey = '昨天' | '7天内' | '30天内' | '更早';

/**
 * 历史会话抽屉（参考 DeepSeek 风格）。
 *
 * <p>从左侧弹出（移动端友好），按 lastActiveAt 自动分组：
 * 昨天 / 7 天内 / 30 天内 / 更早。当前会话高亮展示。</p>
 */
const SessionList: React.FC<Props> = ({
  visible,
  onClose,
  sessions,
  currentSessionId,
  loading,
  onSelect,
  onNewSession,
}) => {
  /** 按分组键聚合会话，分组内部按时间倒序 */
  const grouped = useMemo(() => {
    const now = dayjs();
    const groups: Record<GroupKey, SessionInfo[]> = {
      昨天: [],
      '7天内': [],
      '30天内': [],
      更早: [],
    };
    sessions.forEach((s) => {
      const t = dayjs(s.lastActiveAt);
      const diffDay = now.diff(t, 'day');
      if (diffDay <= 0) groups['昨天'].push(s);
      else if (diffDay <= 7) groups['7天内'].push(s);
      else if (diffDay <= 30) groups['30天内'].push(s);
      else groups['更早'].push(s);
    });
    // 组内按时间倒序
    Object.values(groups).forEach((list) => list.sort((a, b) => (a.lastActiveAt < b.lastActiveAt ? 1 : -1)));
    return groups;
  }, [sessions]);

  const renderItem = (s: SessionInfo) => {
    const isCurrent = s.sessionId === currentSessionId;
    return (
      <div
        key={s.sessionId}
        className={`session-list-item${isCurrent ? ' session-list-item--active' : ''}`}
        onClick={() => {
          if (!isCurrent) {
            onSelect(s.sessionId);
            onClose();
          }
        }}
      >
        <span className="session-list-item-title">{s.title || '未命名会话'}</span>
      </div>
    );
  };

  const groupOrder: GroupKey[] = ['昨天', '7天内', '30天内', '更早'];

  return (
    <Popup
      visible={visible}
      onMaskClick={onClose}
      onClose={onClose}
      position="left"
      bodyStyle={{
        width: '78vw',
        maxWidth: 320,
        height: '100%',
        background: '#1f1f1f',
        color: '#e8e8e8',
      }}
    >
      <div className="session-list">
        {/* 顶部：开启新对话按钮 */}
        <div className="session-list-header">
          <button type="button" className="session-list-new" onClick={onNewSession}>
            <span className="session-list-new-icon">✦</span>
            <span>开启新对话</span>
          </button>
        </div>

        {/* 列表区 */}
        <div className="session-list-body">
          {loading && sessions.length === 0 && (
            <div className="session-list-empty">加载中…</div>
          )}
          {!loading && sessions.length === 0 && (
            <div className="session-list-empty">暂无历史会话</div>
          )}
          {groupOrder.map((key) => {
            const list = grouped[key];
            if (list.length === 0) return null;
            return (
              <div key={key} className="session-list-group">
                <div className="session-list-group-title">{key}</div>
                {list.map(renderItem)}
              </div>
            );
          })}
        </div>
      </div>
    </Popup>
  );
};

export default SessionList;