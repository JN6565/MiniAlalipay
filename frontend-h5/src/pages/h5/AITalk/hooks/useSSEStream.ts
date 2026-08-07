import { useState, useRef, useCallback } from 'react';
import { streamMessage, SSEEvent, StreamMessageParams } from '@/services/ai';

interface UseSSEStreamOptions {
  /** 收到文本增量 */
  onContentDelta: (delta: string) => void;
  /** 收到澄清事件 */
  onClarification: (question: string, options: { id: string; label: string }[]) => void;
  /** 工具调用开始 */
  onToolCall: (toolName: string) => void;
  /** 工具调用完成 */
  onToolResult: (toolName: string, status: string, summary: string, data: Record<string, any>) => void;
  /** 流式完成 */
  onDone: (sessionId: string, messageId: string, intent: string) => void;
  /** 发生错误 */
  onError: (message: string) => void;
  /** 阶段状态更新（如 INTENT、TOOL_CALL、DONE 等） */
  onStatus?: (stage: string, message: string) => void;
}

/**
 * SSE 流式消费 hook。
 *
 * <p>封装 {@link streamMessage} 的调用和 AbortController 生命周期管理，
 * 将 agent-content / agent-tool-call / agent-tool-result / agent-clarification / agent-done / agent-error
 * 全部分发给回调。</p>
 */
export function useSSEStream(options: UseSSEStreamOptions) {
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const { onContentDelta, onClarification, onToolCall, onToolResult, onDone, onError, onStatus } = options;

  /** 发起流式请求 */
  const startStream = useCallback(
    (params: StreamMessageParams) => {
      abortRef.current?.abort();

      setStreaming(true);

      const controller = streamMessage(
        params,
        (event: SSEEvent) => {
          switch (event.type) {
            case 'agent-content':
              onContentDelta(event.data.delta);
              break;
            case 'agent-tool-call':
              onToolCall(event.data.tool);
              break;
            case 'agent-tool-result':
              onToolResult(
                event.data.tool,
                event.data.status,
                event.data.summary,
                event.data.data || {},
              );
              break;
            case 'agent-clarification':
              onClarification(
                event.data.question,
                event.data.options || [],
              );
              break;
            case 'agent-done':
              setStreaming(false);
              onDone(event.data.sessionId, event.data.messageId, event.data.intent);
              break;
            case 'agent-error':
              setStreaming(false);
              onError(event.data.message || 'AI 请求失败');
              break;
            case 'agent-status':
              // 阶段状态事件，用于前端展示处理进度
              onStatus?.(event.data.stage, event.data.message);
              break;
            default:
              break;
          }
        },
        (err: Error) => {
          setStreaming(false);
          onError(err.message || '网络异常');
        },
      );

      abortRef.current = controller;
    },
    [onContentDelta, onClarification, onToolCall, onToolResult, onDone, onError, onStatus],
  );

  /** 取消当前流 */
  const cancelStream = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  }, []);

  return { streaming, startStream, cancelStream };
}
