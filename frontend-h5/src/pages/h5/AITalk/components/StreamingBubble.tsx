import React, { useState, useEffect, useRef } from 'react';
import { AssistantTextMessage } from '../types';

interface Props {
  message: AssistantTextMessage;
}

const StreamingBubble: React.FC<Props> = ({ message }) => {
  const [visibleLen, setVisibleLen] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const full = message.content?.length || 0;

    const tick = () => {
      setVisibleLen((prev) => {
        const next = prev + 1;
        if (next >= full) {
          if (timerRef.current) clearInterval(timerRef.current);
          return full;
        }
        return next;
      });
    };

    // 清除旧定时器，启动新定时器
    if (timerRef.current) clearInterval(timerRef.current);
    if (visibleLen < full) {
      timerRef.current = setInterval(tick, 30);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [message.content]);

  const display = message.content?.substring(0, visibleLen) || '...';
  const isTyping = visibleLen < (message.content?.length || 0);

  if (!message.content && message.streaming) {
    return (
      <div className="ai-message ai-message-assistant">
        <div className="ai-message-content ai-streaming">...</div>
      </div>
    );
  }

  return (
    <div className="ai-message ai-message-assistant">
      <div className={`ai-message-content ${message.streaming ? 'ai-streaming' : ''}`}>
        {display}
        {(message.streaming || isTyping) && <span className="ai-cursor">|</span>}
      </div>
    </div>
  );
};

export default StreamingBubble;
