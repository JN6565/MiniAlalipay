import { useRef, useState } from 'react';
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

        let eventType = '';
        let data = '';
        for (let line of lines) {
          line = line.replace(/\r$/, '');
          if (line.startsWith('event: ')) {
            eventType = line.slice(7).trim();
          } else if (line.startsWith('data: ')) {
            data = line.slice(6);
          } else if (line === '' && eventType && data) {
            try {
              const parsed = JSON.parse(data);
              const handler = (handlers as Record<string, Function>)[eventType];
              handler?.(parsed);
            } catch { /* 解析失败跳过 */ }
            eventType = '';
            data = '';
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
