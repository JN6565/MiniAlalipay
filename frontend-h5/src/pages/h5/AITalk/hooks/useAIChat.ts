import { useState, useCallback } from 'react';
import { sendMessage as sendMessageApi, SendMessageResult } from '@/services/ai';

interface ChatParams {
  clientMessageId: string;
  sessionId?: string;
  content: string;
}

/** 保持旧导出名，兼容页面引用 */
export type ChatResponse = SendMessageResult;

/**
 * AI 对话发送 hook。
 * 统一走 services/ai.ts（axios 拦截器：信封解析、401 跳登录、60 秒超时），
 * 不再使用裸 fetch，避免错误格式不一致与超时失控。
 */
export function useAIChat() {
  const [loading, setLoading] = useState(false);

  const sendMessage = useCallback(async (params: ChatParams): Promise<ChatResponse> => {
    setLoading(true);
    try {
      return await sendMessageApi(params);
    } finally {
      setLoading(false);
    }
  }, []);

  return { sendMessage, loading };
}
