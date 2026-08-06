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
        if (done) { console.log('[SSE] stream done'); break; }

        const chunk = decoder.decode(value, { stream: true });
        console.log('[SSE] raw chunk:', chunk.substring(0, 200));
        buffer += chunk;

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        console.log('[SSE] lines count:', lines.length, 'buffer remainder:', buffer.substring(0, 50));

        let eventType = '';
        let data = '';
        for (let line of lines) {
          line = line.replace(/\r$/, '');
          if (line.startsWith('event:')) {
            eventType = line.slice(6).replace(/^ /, '');
            console.log('[SSE] found event:', eventType);
          } else if (line.startsWith('data:')) {
            data = line.slice(5).replace(/^ /, '');
            console.log('[SSE] found data:', data.substring(0, 80));
          } else if (line === '' && eventType && data) {
            console.log('[SSE] DISPATCH:', eventType);
            try {
              const parsed = JSON.parse(data);
              const handler = (handlers as Record<string, Function>)[eventType];
              if (handler) {
                handler(parsed);
              } else {
                console.warn('[SSE] no handler for:', eventType);
              }
            } catch(e) { console.error('[SSE] parse error:', e); }
            eventType = '';
            data = '';
          } else if (line === '') {
            console.log('[SSE] blank line (no event/data pending)');
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
