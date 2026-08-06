import React, { useState, useEffect, useRef } from 'react';
import { AssistantTextMessage } from '../types';

interface Props {
  message: AssistantTextMessage;
}

const StreamingBubble: React.FC<Props> = ({ message }) => {
  const [visibleLen, setVisibleLen] = useState(0);
  const prevContentLen = useRef(0);

  useEffect(() => {
    const targetLen = message.content.length;
    if (targetLen <= prevContentLen.current) {
      // 内容没变化，不需要重新打字
      prevContentLen.current = targetLen;
      return;
    }
    prevContentLen.current = targetLen;

    // 逐字显示新增内容
    const timer = setInterval(() => {
      setVisibleLen((prev) => {
        if (prev >= targetLen) {
          clearInterval(timer);
          return prev;
        }
        return prev + 1;
      });
    }, 25); // ~40 字/秒，接近真实打字速度

    return () => clearInterval(timer);
  }, [message.content]);

  // streaming 结束时确保显示全部
  const display = message.streaming
    ? message.content.substring(0, visibleLen)
    : message.content;

  return (
    <div className="ai-message ai-message-assistant">
      <div className={`ai-message-content ${message.streaming ? 'ai-streaming' : ''}`}>
        {display || '...'}
        {message.streaming && visibleLen < (message.content?.length || 0) && (
          <span className="ai-cursor">|</span>
        )}
      </div>
    </div>
  );
};

export default StreamingBubble;
