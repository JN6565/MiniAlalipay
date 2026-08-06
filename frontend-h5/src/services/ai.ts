import request from './request';

/** AI 消息请求参数 */
interface SendMessageParams {
  clientMessageId: string;
  sessionId?: string;
  content: string;
}

/** AI 消息响应 */
interface SendMessageResult {
  sessionId: string;
  messageId: string;
  content: string;
  intent: string;
  slots: Record<string, any>;
  clarificationNeeded: boolean;
}

/**
 * 发送消息给 AI
 * @param params 消息参数
 * @returns AI 回复
 */
export async function sendMessage(params: SendMessageParams): Promise<SendMessageResult> {
  return request.post('/v1/agent/messages', params);
}
