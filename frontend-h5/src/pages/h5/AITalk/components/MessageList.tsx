import React, { useRef, useEffect, ReactNode } from 'react';
import { Message } from '../types';

interface Props {
  messages: Message[];
  renderMessage: (msg: Message, index: number) => ReactNode;
}

const MessageList: React.FC<Props> = ({ messages, renderMessage }) => {
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="ai-messages">
      {messages.length === 0 && (
        <div className="ai-welcome">
          <div className="ai-welcome-desc">我可以帮你转账、查余额、查账单等</div>
        </div>
      )}
      {messages.map((msg, i) => (
        <div key={msg.id}>{renderMessage(msg, i)}</div>
      ))}
      <div ref={endRef} />
    </div>
  );
};

export default MessageList;
