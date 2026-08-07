import { useState, useCallback } from 'react';
import { getSessions, getSessionMessages } from '@/services/ai';
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
      const list = await getSessions();
      setSessions(list);
    } catch {
      // 静默失败：会话列表非关键路径
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

  return {
    sessionId,
    sessions,
    sessionsLoading,
    saveSessionId,
    clearSession,
    loadSessions,
    switchSession,
  };
}
