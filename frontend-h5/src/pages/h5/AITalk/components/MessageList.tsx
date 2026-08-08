import React, { useRef, useEffect, useCallback, ReactNode } from 'react';
import { Message } from '../types';

interface Props {
  messages: Message[];
  renderMessage: (msg: Message, index: number) => ReactNode;
  /** 欢迎页建议词条点击 */
  onSuggestionClick?: (text: string) => void;
}

/** 欢迎页快捷建议：与后端 8 类意图对应，降低用户输入成本 */
const SUGGESTIONS: { icon: string; text: string }[] = [
  { icon: '💰', text: '查一下我的余额' },
  { icon: '💸', text: '给张三转账100元' },
  { icon: '📋', text: '查看最近的交易记录' },
  { icon: '🌸', text: '我的花呗账单' },
];

/** 距底部多少像素内视为"贴底"，贴底时新消息才自动滚动 */
const NEAR_BOTTOM_PX = 120;

const MessageList: React.FC<Props> = ({ messages, renderMessage, onSuggestionClick }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  /** 用户是否贴底：向上翻历史时不强制滚底 */
  const stickToBottomRef = useRef(true);

  const handleScroll = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    stickToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
  }, []);

  useEffect(() => {
    if (stickToBottomRef.current) {
      endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  return (
    <div className="ai-messages" ref={containerRef} onScroll={handleScroll}>
      {messages.length === 0 && (
        <div className="ai-welcome">
          <div className="ai-welcome-avatar">🐱</div>
          <div className="ai-welcome-title">你好，我是财喵</div>
          <div className="ai-welcome-desc">
            转账、查余额、查账单、花呗还款
            <br />
            一句话搞定
          </div>
          <div className="ai-suggestions">
            {SUGGESTIONS.map((s) => (
              <button
                key={s.text}
                type="button"
                className="ai-suggestion-chip"
                onClick={() => onSuggestionClick?.(s.text)}
              >
                <span className="ai-suggestion-icon">{s.icon}</span>
                {s.text}
              </button>
            ))}
          </div>
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