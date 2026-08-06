import React, { useRef, useEffect } from 'react';
import { AssistantTextMessage } from '../types';

interface Props {
  message: AssistantTextMessage;
}

const StreamingBubble: React.FC<Props> = ({ message }) => {
  const textRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastContentRef = useRef('');

  useEffect(() => {
    if (!textRef.current) return;
    const full = message.content || '';

    // 内容没变，跳过
    if (full === lastContentRef.current) return;
    lastContentRef.current = full;

    // 清除旧定时器
    if (timerRef.current) clearInterval(timerRef.current);

    if (!full) {
      textRef.current.textContent = '...';
      return;
    }

    // 直接操作 DOM 逐字动画，不经过 React state
    let i = textRef.current.textContent === '...' ? 0 : textRef.current.textContent!.length;
    textRef.current.textContent = full.substring(0, i);

    timerRef.current = setInterval(() => {
      i++;
      if (textRef.current) {
        textRef.current.textContent = full.substring(0, i);
      }
      if (i >= full.length && timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }, 25);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [message.content]);

  return (
    <div className="ai-message ai-message-assistant">
      <div
        ref={textRef}
        className={`ai-message-content ${message.streaming ? 'ai-streaming' : ''}`}
      >
        {message.content || '...'}
      </div>
    </div>
  );
};

export default StreamingBubble;
