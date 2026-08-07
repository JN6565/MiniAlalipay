import React from 'react';
import { AssistantTextMessage } from '../types';
import MarkdownContent from './MarkdownContent';
import ThinkingBubble from './ThinkingBubble';
import MessageActions from './MessageActions';

interface Props {
  message: AssistantTextMessage;
  /** 反馈变更回调 */
  onFeedbackChange: (id: string, next: 'like' | 'dislike' | null) => void;
  /** 重新生成回调 */
  onRegenerate: (id: string) => void;
}

/**
 * 流式回复气泡：参考 DeepSeek 风格，支持思考过程展示与操作按钮行。
 *
 * <p>思考过程通过 {@link AssistantTextMessage.thinking} 传入；
 * 思考耗时基于消息创建时间到当前时间的差值粗略计算。</p>
 */
const StreamingBubble: React.FC<Props> = ({ message, onFeedbackChange, onRegenerate }) => {
  // 思考耗时：未传 seconds 时基于时间戳粗略计算
  const seconds =
    typeof message.thinkingSeconds === 'number'
      ? message.thinkingSeconds
      : Math.max(0, (Date.now() - message.timestamp.getTime()) / 1000);

  // 流式中或思考过程为空时不展示操作按钮；思考过程仍可见
  const showActions = !message.streaming && message.showActions !== false;

  return (
    <div className="ai-message ai-message-assistant">
      <div className="ai-message-body">
        {/* 思考过程折叠面板（位于内容上方，DeepSeek 同位） */}
        <ThinkingBubble thinking={message.thinking ?? ''} seconds={seconds} />

        {/* 文本主体：空内容时显示三点动画 */}
        <div className="ai-message-content">
          {message.content ? (
            <>
              <MarkdownContent content={message.content} />
              {message.streaming && <span className="ai-cursor">|</span>}
            </>
          ) : (
            <span className="ai-typing-inline">
              <span className="ai-typing-dot" />
              <span className="ai-typing-dot" />
              <span className="ai-typing-dot" />
            </span>
          )}
        </div>

        {/* 操作按钮行（复制 / 重新生成 / 点赞 / 点踩） */}
        {showActions && (
          <MessageActions
            content={message.content}
            feedback={message.feedback ?? null}
            onFeedbackChange={(next) => onFeedbackChange(message.id, next)}
            onRegenerate={() => onRegenerate(message.id)}
          />
        )}
      </div>
    </div>
  );
};

export default StreamingBubble;