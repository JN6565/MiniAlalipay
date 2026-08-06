import { useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { StreamHandlers } from '../types';

function getToken(): string {
  return localStorage.getItem('accessToken') || '';
}

interface StreamParams {
  clientMessageId: string;
  sessionId?: string;
  content: string;
}

export function useSSEStream() {
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const startStream = async (
    params: StreamParams,
    handlers: Partial<StreamHandlers>,
  ): Promise<void> => {
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);

    try {
      const response = await fetch('/api/v1/agent/messages/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getToken()}`,
          'X-Request-Id': crypto.randomUUID?.() || `req_${Date.now()}`,
        },
        body: JSON.stringify(params),
        signal: controller.signal,
      });

      if (!response.ok) {
        const text = await response.text();
        handlers['agent-error']?.({ code: `HTTP_${response.status}`, message: text });
        return;
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        // 先收集所有事件
        const events: Array<{ type: string; payload: any }> = [];
        let eventType = '';
        let data = '';
        for (let line of lines) {
          line = line.replace(/\r$/, '');
          if (line.startsWith('event:')) {
            eventType = line.slice(6).replace(/^ /, '');
          } else if (line.startsWith('data:')) {
            data = line.slice(5).replace(/^ /, '');
          } else if (line === '' && eventType && data) {
            try {
              events.push({ type: eventType, payload: JSON.parse(data) });
            } catch { /* skip */ }
            eventType = '';
            data = '';
          }
        }

        // 逐个事件同步渲染 + 延迟，产生逐句流式效果
        for (const evt of events) {
          const handler = (handlers as Record<string, Function>)[evt.type];
          if (handler && evt.type === 'agent-content') {
            // content 事件用 flushSync 强制立即渲染
            flushSync(() => handler(evt.payload));
            await new Promise((r) => setTimeout(r, 80));
          } else {
            handler?.(evt.payload);
          }
        }
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        handlers['agent-error']?.({ code: 'NETWORK_ERROR', message: err.message });
      }
    } finally {
      setStreaming(false);
    }
  };

  const abort = () => {
    abortRef.current?.abort();
    setStreaming(false);
  };

  return { startStream, abort, streaming };
}
