import request from './request';
import type { SessionInfo, HistoryMessage } from '@/pages/h5/AITalk/types';

/** 发送消息入参：clientMessageId 为客户端幂等键，sessionId 为空表示新建会话 */
interface SendMessageParams {
  clientMessageId: string;
  sessionId?: string;
  content: string;
}

/** AI 回复数据（契约见 contracts/openapi/minialalipay-api.yaml 的 SendAgentMessageResponseData） */
export interface SendMessageResult {
  sessionId: string;
  messageId: string;
  content: string;
  intent: string;
  slots: Record<string, any>;
  clarificationNeeded: boolean;
}

/** SSE 流式事件类型 */
export type SSEEventType =
  | 'agent-status'
  | 'agent-tool-call'
  | 'agent-tool-result'
  | 'agent-content'
  | 'agent-clarification'
  | 'agent-done'
  | 'agent-error';

/** SSE 事件载荷 */
export interface SSEEvent {
  type: SSEEventType;
  data: any;
}

/** 流式消息发送参数（与同步接口相同） */
export type StreamMessageParams = SendMessageParams;

/** LLM 推理耗时较长，单独放宽到 60 秒，避免默认 10 秒超时误杀正常回复 */
const AI_REQUEST_TIMEOUT_MS = 60000;

/**
 * 发送消息给 AI 助手（同步接口）。
 * 统一走 axios 封装：自动注入 Authorization / X-Request-Id，401 自动跳登录，错误抛出 ApiError。
 */
export async function sendMessage(params: SendMessageParams): Promise<SendMessageResult> {
  return request.post('/api/v1/agent/messages', params, { timeout: AI_REQUEST_TIMEOUT_MS });
}

/**
 * 流式发送消息给 AI 助手（SSE 接口）。
 * 使用 fetch + ReadableStream 消费 POST 响应的 SSE 流。
 * EventSource 不支持 POST，因此使用原生 fetch。
 *
 * @param params 消息参数
 * @param onEvent SSE 事件回调
 * @param onError 错误回调
 * @returns AbortController，用于取消请求
 */
export function streamMessage(
  params: StreamMessageParams,
  onEvent: (event: SSEEvent) => void,
  onError: (error: Error) => void,
): AbortController {
  const controller = new AbortController();

  const doFetch = async () => {
    const token = localStorage.getItem('accessToken');
    const requestId = crypto.randomUUID?.() || Math.random().toString(36).slice(2);

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Request-Id': requestId,
    };
    if (token) headers.Authorization = `Bearer ${token}`;

    const response = await fetch('/api/v1/agent/messages/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(params),
      signal: controller.signal,
      cache: 'no-store', // 禁止浏览器缓存 SSE 流
    });

    if (!response.ok) {
      const text = await response.text().catch(() => '');
      let message = `HTTP ${response.status}`;
      try {
        const json = JSON.parse(text);
        if (json.message) message = json.message;
      } catch { /* 非 JSON 响应 */ }
      throw new Error(message);
    }

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    /** 当前 SSE 事件名，跨 chunk 保持状态 */
    let currentEvent = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim();
          if (currentEvent && dataStr) {
            try {
              const parsedEvent: SSEEvent = {
                type: currentEvent as SSEEventType,
                data: JSON.parse(dataStr),
              };
              // 使用 setTimeout(fn, 0) 将每个事件推入独立宏任务，
              // 打破 React 18 的自动批处理，确保每次内容增量都触发独立渲染，
              // 从而保持逐字出现的流式显示效果。
              setTimeout(() => onEvent(parsedEvent), 0);
            } catch {
              /* 跳过无法解析的数据 */
            }
          }
        } else if (line.trim() === '') {
          currentEvent = '';
        }
      }
    }
  };

  doFetch().catch((err: any) => {
    if (err.name !== 'AbortError') {
      onError(err instanceof Error ? err : new Error(String(err)));
    }
  });

  return controller;
}

/**
 * 确认提交（高风险操作：转账/还款）。
 * 注意：后端 /api/v1/confirmations 端点尚未实现，当前预留。
 */
export async function confirmSubmission(
  draftId: string,
  password: string,
  idempotencyKey: string,
): Promise<any> {
  return request.post('/api/v1/confirmations', {
    draftId,
    paymentPassword: password,
  }, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/**
 * 获取当前用户的所有活跃会话列表（按最后活跃时间倒序）。
 */
export async function getSessions(): Promise<SessionInfo[]> {
  return request.get('/api/v1/agent/sessions');
}

/**
 * 获取指定会话的消息历史（按时间正序）。
 */
export async function getSessionMessages(sessionId: string): Promise<HistoryMessage[]> {
  return request.get(`/api/v1/agent/sessions/${sessionId}/messages`);
}

/**
 * 软删除会话：将状态设为 CLOSED，不再出现在活跃列表中。
 */
export async function deleteSession(sessionId: string): Promise<void> {
  return request.delete(`/api/v1/agent/sessions/${sessionId}`);
}

/**
 * 重命名会话：更新用户自定义标题。传空字符串清除自定义标题。
 */
export async function renameSession(sessionId: string, title: string): Promise<void> {
  return request.patch(`/api/v1/agent/sessions/${sessionId}/title`, { title });
}
