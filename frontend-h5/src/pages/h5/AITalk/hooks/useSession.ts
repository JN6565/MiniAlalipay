import { useState, useCallback } from 'react';
import { getSessions, getSessionMessages, deleteSession, renameSession } from '@/services/ai';
import type { SessionInfo, HistoryMessage, Message } from '../types';

const SESSION_KEY = 'ai_session_id';

export function useSession() {
  const [sessionId, setSessionIdState] = useState<string | null>(() => {
    try {
      return localStorage.getItem(SESSION_KEY) ?? null;
    } catch {
      return null;
    }
  });

  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);

  const saveSessionId = useCallback((id: string) => {
    setSessionIdState(id);
    try {
      localStorage.setItem(SESSION_KEY, id);
    } catch { /* ignore */ }
  }, []);

  const clearSession = useCallback(() => {
    setSessionIdState(null);
    try {
      localStorage.removeItem(SESSION_KEY);
    } catch { /* ignore */ }
  }, []);

  /** 加载会话列表（触发于打开抽屉时或新建会话后） */
  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    try {
      console.log('[useSession] 开始加载会话列表...');
      const list = await getSessions();
      console.log('[useSession] 会话列表加载成功:', list);
      setSessions(list);
    } catch (err: any) {
      // 会话列表非关键路径，静默失败但保留详细日志便于排查
      console.error('[useSession] 加载会话列表失败:', {
        message: err?.message,
        code: err?.code,
        status: err?.status,
        requestId: err?.requestId,
      });
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  /**
   * 切换到指定会话：拉取历史消息并重建消息列表。
   *
   * @param targetSessionId 目标会话 ID
   * @returns 重建后的消息数组
   */
  const switchSession = useCallback(
    async (targetSessionId: string): Promise<Message[]> => {
      saveSessionId(targetSessionId);
      try {
        const history: HistoryMessage[] = await getSessionMessages(targetSessionId);
        return history.map((m, i) => {
          if (m.role === 'USER') {
            return {
              id: `hist_user_${i}`,
              role: 'user' as const,
              content: m.content,
              timestamp: new Date(m.createdAt),
            };
          }
          return {
            id: `hist_assist_${i}`,
            role: 'assistant' as const,
            kind: 'text' as const,
            content: m.content,
            streaming: false,
            timestamp: new Date(m.createdAt),
          };
        });
      } catch {
        // 加载失败时返回空列表，保持当前会话
        return [];
      }
    },
    [saveSessionId],
  );

  /**
   * 删除会话：调用后端软删除接口，成功后从列表中移除并刷新。
   *
   * @param targetSessionId 要删除的会话 ID
   */
  const removeSession = useCallback(
    async (targetSessionId: string) => {
      try {
        await deleteSession(targetSessionId);
        setSessions((prev) => prev.filter((s) => s.sessionId !== targetSessionId));
      } catch (err: any) {
        console.error('[useSession] 删除会话失败:', err?.message);
        throw err;
      }
    },
    [],
  );

  /**
   * 重命名会话：调用后端更新标题接口，成功后更新本地列表。
   *
   * @param targetSessionId 会话 ID
   * @param newTitle 新标题
   */
  const renameSessionTitle = useCallback(
    async (targetSessionId: string, newTitle: string) => {
      try {
        await renameSession(targetSessionId, newTitle);
        setSessions((prev) =>
          prev.map((s) =>
            s.sessionId === targetSessionId ? { ...s, title: newTitle || s.title } : s,
          ),
        );
      } catch (err: any) {
        console.error('[useSession] 重命名会话失败:', err?.message);
        throw err;
      }
    },
    [],
  );

  return {
    sessionId,
    sessions,
    sessionsLoading,
    saveSessionId,
    clearSession,
    loadSessions,
    switchSession,
    removeSession,
    renameSessionTitle,
  };
}
