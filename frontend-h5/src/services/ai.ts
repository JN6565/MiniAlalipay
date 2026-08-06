import request from './request';

interface SendMessageParams {
  clientMessageId: string;
  sessionId?: string;
  content: string;
}

interface SendMessageResult {
  sessionId: string;
  messageId: string;
  content: string;
  intent: string;
  slots: Record<string, any>;
  clarificationNeeded: boolean;
}

/**
 * 发送消息给 AI（同步，兼容保留）
 */
export async function sendMessage(params: SendMessageParams): Promise<SendMessageResult> {
  return request.post('/api/v1/agent/messages', params);
}

/**
 * 确认提交（高风险操作：转账/还款）
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
