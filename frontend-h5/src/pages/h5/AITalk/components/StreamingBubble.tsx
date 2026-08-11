import React from 'react';
import { AssistantTextMessage } from '../types';
import MarkdownContent from './MarkdownContent';
import ThinkingBubble from './ThinkingBubble';
import MessageActions from './MessageActions';
import ConfirmationCard from './ConfirmationCard';
import ToolResultCard from './ToolResultCard';

interface Props {
  message: AssistantTextMessage;
  /** 反馈变更回调 */
  onFeedbackChange: (id: string, next: 'like' | 'dislike' | null) => void;
  /** 重新生成回调 */
  onRegenerate: (id: string) => void;
  /** 确认卡片回调（可选） */
  onConfirmTransfer?: (draftId: string, payeeId: string, amountFen: number, password: string, version?: number) => Promise<void>;
  onCancelTransfer?: (draftId: string) => void;
}

/**
 * 流式回复气泡：参考 DeepSeek 风格，支持思考过程展示与操作按钮行。
 *
 * <p>思考过程通过 {@link AssistantTextMessage.thinking} 传入；
 * 思考耗时基于消息创建时间到当前时间的差值粗略计算。</p>
 */
const StreamingBubble: React.FC<Props> = ({ message, onFeedbackChange, onRegenerate, onConfirmTransfer, onCancelTransfer }) => {
  // 思考耗时：未传 seconds 时基于时间戳粗略计算
  const seconds =
    typeof message.thinkingSeconds === 'number'
      ? message.thinkingSeconds
      : Math.max(0, (Date.now() - message.timestamp.getTime()) / 1000);

  // 流式中或思考过程为空时不展示操作按钮；思考过程仍可见
  const showActions = !message.streaming && message.showActions !== false;

  // 是否有内嵌卡片
  const hasCard = !!(message.toolResultCards?.length || (message.confirmationCard && onConfirmTransfer && onCancelTransfer));

  return (
    <div className="ai-message ai-message-assistant">
      <div className="ai-assistant-orb" aria-hidden="true">🐱</div>
      <div className="ai-message-body">
        {/* 思考过程折叠面板 */}
        <ThinkingBubble thinking={message.thinking ?? ''} seconds={seconds} />

        {/* 消息内容气泡：文本 + 内嵌卡片共用同一个背景 */}
        {(message.content || hasCard) && (
          <div className="ai-message-content">
            {/* 文本内容 */}
            {message.content ? (
              <>
                <MarkdownContent content={message.content} />
                {message.streaming && <span className="ai-cursor">|</span>}
              </>
            ) : null}

            {/* 内嵌确认卡片（转账/还款二次确认） */}
            {message.confirmationCard && onConfirmTransfer && onCancelTransfer && (
              <ConfirmationCard
                message={{
                  id: message.id,
                  role: 'assistant',
                  kind: 'confirmation',
                  ...message.confirmationCard,
                  timestamp: message.timestamp,
                }}
                onConfirm={onConfirmTransfer}
                onCancel={onCancelTransfer}
              />
            )}

            {/* 内嵌工具结果卡片列表（余额、额度、交易记录等） */}
            {message.toolResultCards?.map((card, idx) => (
              <ToolResultCard
                key={`${card.tool}_${idx}`}
                message={{
                  id: `${message.id}_tr_${idx}`,
                  role: 'assistant',
                  kind: 'tool-result',
                  ...card,
                  loading: false,
                  timestamp: message.timestamp,
                }}
              />
            ))}
          </div>
        )}

        {/* 无内容且无卡片时：显示三点加载动画 */}
        {!message.content && !hasCard && (
          <div className="ai-message-content">
            <span className="ai-typing-inline">
              <span className="ai-typing-dot" />
              <span className="ai-typing-dot" />
              <span className="ai-typing-dot" />
            </span>
          </div>
        )}

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
