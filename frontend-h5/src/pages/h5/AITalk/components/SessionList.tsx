import React, { useMemo, useState, useRef, useEffect } from 'react';
import { Popup, Dialog, Input } from 'antd-mobile';
import dayjs from 'dayjs';
import type { SessionInfo } from '../types';

interface Props {
  visible: boolean;
  onClose: () => void;
  sessions: SessionInfo[];
  currentSessionId: string | null;
  loading: boolean;
  onSelect: (sessionId: string) => void;
  onDelete: (sessionId: string) => void;
  onRename: (sessionId: string, newTitle: string) => void;
}

/** 会话分组键 */
type GroupKey = '昨天' | '7天内' | '30天内' | '更早';

/**
 * 历史会话抽屉（参考 DeepSeek 风格）。
 *
 * <p>从左侧弹出（移动端友好），按 lastActiveAt 自动分组：
 * 昨天 / 7 天内 / 30 天内 / 更早。当前会话高亮展示。
 * 每个会话项支持左滑删除和长按编辑名称。</p>
 */
const SessionList: React.FC<Props> = ({
  visible,
  onClose,
  sessions,
  currentSessionId,
  loading,
  onSelect,
  onDelete,
  onRename,
}) => {
  /** 当前正在编辑标题的会话 ID，null 表示非编辑状态 */
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const editInputRef = useRef<HTMLInputElement>(null);

  /** 编辑模式下自动聚焦输入框 */
  useEffect(() => {
    if (editingId && editInputRef.current) {
      editInputRef.current.focus();
    }
  }, [editingId]);

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

  /** 确认删除会话 */
  const handleDelete = async (sessionId: string) => {
    const result = await Dialog.confirm({
      content: '确定删除该会话？删除后不可恢复。',
      confirmText: '删除',
      cancelText: '取消',
    });
    if (result) {
      onDelete(sessionId);
    }
  };

  /** 开始编辑标题 */
  const startEditing = (s: SessionInfo) => {
    setEditingId(s.sessionId);
    setEditValue(s.title || '');
  };

  /** 提交编辑 */
  const submitEdit = () => {
    if (editingId) {
      const trimmed = editValue.trim();
      if (trimmed) {
        onRename(editingId, trimmed);
      }
      setEditingId(null);
      setEditValue('');
    }
  };

  const renderItem = (s: SessionInfo) => {
    const isCurrent = s.sessionId === currentSessionId;
    const isEditing = editingId === s.sessionId;

    return (
      <div
        key={s.sessionId}
        className={`session-list-item${isCurrent ? ' session-list-item--active' : ''}`}
      >
        {isEditing ? (
          /* 编辑模式 */
          <div className="session-list-item-edit">
            <Input
              ref={editInputRef as any}
              className="session-list-item-input"
              value={editValue}
              onChange={setEditValue}
              maxLength={100}
              placeholder="输入会话名称"
              onKeyDown={(e) => {
                if (e.key === 'Enter') submitEdit();
                if (e.key === 'Escape') {
                  setEditingId(null);
                  setEditValue('');
                }
              }}
            />
            <div className="session-list-item-edit-actions">
              <button
                type="button"
                className="session-edit-btn session-edit-confirm"
                onClick={submitEdit}
              >
                ✓
              </button>
              <button
                type="button"
                className="session-edit-btn session-edit-cancel"
                onClick={() => {
                  setEditingId(null);
                  setEditValue('');
                }}
              >
                ✕
              </button>
            </div>
          </div>
        ) : (
          /* 正常展示模式 */
          <>
            <span
              className="session-list-item-title"
              onClick={() => {
                if (!isCurrent) {
                  onSelect(s.sessionId);
                  onClose();
                }
              }}
            >
              {s.title || '未命名会话'}
            </span>
            <div className="session-list-item-actions">
              <button
                type="button"
                className="session-action-btn session-action-edit"
                onClick={(e) => {
                  e.stopPropagation();
                  startEditing(s);
                }}
                aria-label="编辑会话名称"
              >
                ✎
              </button>
              <button
                type="button"
                className="session-action-btn session-action-delete"
                onClick={(e) => {
                  e.stopPropagation();
                  handleDelete(s.sessionId);
                }}
                aria-label="删除会话"
              >
                🗑
              </button>
            </div>
          </>
        )}
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
