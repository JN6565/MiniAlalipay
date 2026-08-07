import React from 'react';
import { ClarificationMessage } from '../types';
import MarkdownContent from './MarkdownContent';

interface Props {
  message: ClarificationMessage;
  /** 点击快捷选项：直接以选项文本发送一条新消息 */
  onSelect: (optionId: string) => void;
}

/** 澄清引导气泡：问题文本 + 可选的快捷回复词条（为空时仅展示问题，允许自由输入） */
const ClarificationBubble: React.FC<Props> = ({ message, onSelect }) => (
  <div className="ai-message ai-message-assistant">
    <div className="ai-avatar ai-avatar-assistant">🤖</div>
    <div className="ai-message-body">
      <div className="ai-message-content ai-clarification">
        <div className="ai-clarification-question">
          <MarkdownContent content={message.question} />
        </div>
        {message.options.length > 0 && (
          <div className="ai-clarification-options">
            {message.options.map((opt) => (
              <button
                key={opt.id}
                type="button"
                className="ai-option-chip"
                onClick={() => onSelect(opt.id)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  </div>
);

export default ClarificationBubble;
