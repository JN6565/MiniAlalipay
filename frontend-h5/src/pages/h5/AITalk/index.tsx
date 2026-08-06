import React, { useState, useRef, useEffect } from 'react';
import { Input, Button, Toast } from 'antd-mobile';
import { SendOutline } from 'antd-mobile-icons';
import { sendMessage } from '@/services/ai';
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
  const [sessionId, setSessionId] = useState<string | null>(null);
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
    const currentInput = inputValue;
    setInputValue('');
    setLoading(true);

    try {
      // 生成客户端消息ID（16-64位）
      const clientMessageId = `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      // 调用 AI 接口
      const result = await sendMessage({
        clientMessageId,
        sessionId: sessionId || undefined,
        content: currentInput,
      });

      // 更新 sessionId
      if (result.sessionId) {
        setSessionId(result.sessionId);
      }

      // 构建 AI 回复消息
      const aiMessage: Message = {
        id: result.messageId || Date.now().toString(),
        role: 'assistant',
        content: result.content,
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, aiMessage]);
    } catch (error: any) {
      console.error('AI 请求失败:', error);
      Toast.show({
        content: error?.message || 'AI 请求失败，请重试',
        position: 'center',
      });

      // 添加错误提示消息
      const errorMessage: Message = {
        id: Date.now().toString(),
        role: 'assistant',
        content: '抱歉，我暂时无法回复。请稍后再试。',
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
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
