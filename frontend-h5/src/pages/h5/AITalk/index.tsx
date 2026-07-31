import React, { useState, useRef, useEffect } from 'react';
import { Input, Button, Toast } from 'antd-mobile';
import { SendOutline } from 'antd-mobile-icons';
import './index.less';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

const AITalkPage: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleSend = async () => {
    if (!inputValue.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: inputValue,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue('');
    setLoading(true);

    // TODO: 调用AI接口
    setTimeout(() => {
      const aiMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: 'AI助手功能开发中...',
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, aiMessage]);
      setLoading(false);
    }, 1000);
  };

  return (
    <div className="ai-talk-page">
      <div className="ai-messages">
        {messages.length === 0 && (
          <div className="ai-welcome">
            <div className="ai-welcome-title">AI助手</div>
            <div className="ai-welcome-desc">
              我可以帮你转账、查余额、查账单等
            </div>
          </div>
        )}
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`ai-message ai-message-${msg.role}`}
          >
            <div className="ai-message-content">{msg.content}</div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div className="ai-input-bar">
        <Input
          className="ai-input"
          placeholder="输入消息..."
          value={inputValue}
          onChange={setInputValue}
          onEnterPress={handleSend}
        />
        <Button
          className="ai-send-btn"
          color="primary"
          fill="solid"
          onClick={handleSend}
          loading={loading}
          disabled={!inputValue.trim()}
        >
          <SendOutline />
        </Button>
      </div>
    </div>
  );
};

export default AITalkPage;
