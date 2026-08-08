import { useState, useCallback } from 'react';
import { getSessions, getSessionMessages, deleteSession, renameSession } from '@/services/ai';
import type { SessionInfo, HistoryMessage, Message, AssistantTextMessage } from '../types';

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
        // 按创建时间正序排序，确保用户消息在 AI 回复之前
        const sorted = [...history].sort((a, b) =>
          new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        const mapped = sorted.map((m, i) => {
          if (m.role === 'USER') {
            return {
              id: `hist_user_${i}`,
              role: 'user' as const,
              content: m.content,
              timestamp: new Date(m.createdAt),
            };
          }
          // 工具结果消息：解析 JSON 内容重建卡片
          if (m.kind === 'TOOL_RESULT') {
            try {
              const parsed = JSON.parse(m.content);
              const toolMsg: AssistantTextMessage = {
                id: `hist_tr_${i}`,
                role: 'assistant' as const,
                kind: 'text' as const,
                content: '',
                streaming: false,
                toolResultCards: [{
                  tool: parsed.tool || m.toolName || '',
                  status: parsed.status || 'success',
                  summary: parsed.summary || '',
                  data: parsed.data || {},
                }],
                timestamp: new Date(m.createdAt),
              };
              return toolMsg;
            } catch {
              // JSON 解析失败时降级为普通文本消息
            }
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
        // 合并相邻的工具结果卡片到同一气泡（与实时流式行为一致）
        const merged: Message[] = [];
        for (const msg of mapped) {
          if (
            msg.role === 'assistant' &&
            msg.kind === 'text' &&
            (msg as AssistantTextMessage).toolResultCards?.length
          ) {
            // 尝试合并到前一条助手文本消息
            const last = merged.length > 0 ? merged[merged.length - 1] : null;
            if (
              last &&
              last.role === 'assistant' &&
              last.kind === 'text' &&
              !(last as AssistantTextMessage).toolResultCards?.length &&
              !(last as AssistantTextMessage).confirmationCard
            ) {
              // 前一条是空文本消息（AI 过渡文本），将卡片合并进去
              (last as AssistantTextMessage).content = ((last as AssistantTextMessage).content || '') +
                ((msg as AssistantTextMessage).content || '');
              (last as AssistantTextMessage).toolResultCards = (msg as AssistantTextMessage).toolResultCards;
              continue;
            }
            if (
              last &&
              last.role === 'assistant' &&
              last.kind === 'text' &&
              (last as AssistantTextMessage).toolResultCards?.length
            ) {
              // 前一条也有卡片，累积到同一气泡
              (last as AssistantTextMessage).toolResultCards!.push(
                ...(msg as AssistantTextMessage).toolResultCards!
              );
              continue;
            }
          }
          merged.push(msg);
        }
        return merged;
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
