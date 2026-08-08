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

/** 距底部多少像素内视为“贴底” */
const NEAR_BOTTOM_PX = 120;
/** 用户手动上滚后，暂停自动滚动的时长（毫秒） */
const SCROLL_PAUSE_MS = 3000;
/** 自动滚动节流间隔（毫秒） */
const SCROLL_THROTTLE_MS = 150;

const MessageList: React.FC<Props> = ({ messages, renderMessage, onSuggestionClick }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  /** 用户是否贴底：向上翻历史时不强制滚底 */
  const stickToBottomRef = useRef(true);
  /** 用户手动上滚后暂停自动滚动的时间戳 */
  const userScrollUntilRef = useRef(0);
  /** 上次自动滚动的时间戳（节流用） */
  const lastScrollAtRef = useRef(0);
  /** 节流定时器 */
  const scrollTimerRef = useRef<ReturnType<typeof setTimeout>>();

  const handleScroll = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
    if (!isNearBottom && stickToBottomRef.current) {
      // 用户手动上滚，暂停自动滚动
      stickToBottomRef.current = false;
      userScrollUntilRef.current = Date.now() + SCROLL_PAUSE_MS;
    } else if (isNearBottom && !stickToBottomRef.current) {
      // 用户滚回底部，恢复自动滚动
      stickToBottomRef.current = true;
    }
  }, []);

  useEffect(() => {
    const now = Date.now();

    // 如果用户手动上滚且暂停时间未到，不自动滚底
    if (!stickToBottomRef.current && now < userScrollUntilRef.current) {
      return;
    }
    // 暂停时间到期后恢复贴底状态
    if (!stickToBottomRef.current && now >= userScrollUntilRef.current) {
      stickToBottomRef.current = true;
    }

    if (!stickToBottomRef.current) return;

    // 节流：至少间隔 SCROLL_THROTTLE_MS 才执行一次滚动
    const elapsed = now - lastScrollAtRef.current;
    if (elapsed < SCROLL_THROTTLE_MS) {
      // 延迟到节流间隔到期
      if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current);
      scrollTimerRef.current = setTimeout(() => {
        const el = containerRef.current;
        if (el && stickToBottomRef.current) {
          lastScrollAtRef.current = Date.now();
          el.scrollTop = el.scrollHeight;
        }
      }, SCROLL_THROTTLE_MS - elapsed);
      return;
    }

    lastScrollAtRef.current = now;
    const el = containerRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }

    return () => {
      if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current);
    };
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
