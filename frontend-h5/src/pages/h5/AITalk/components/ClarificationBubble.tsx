import React from 'react';
import { Button } from 'antd-mobile';
import { ClarificationMessage } from '../types';

interface Props {
  message: ClarificationMessage;
  onSelect: (optionId: string) => void;
}

const ClarificationBubble: React.FC<Props> = ({ message, onSelect }) => (
  <div className="ai-message ai-message-assistant">
    <div className="ai-message-content ai-clarification">
      <div className="ai-clarification-question">{message.question}</div>
      {message.options.length > 0 && (
        <div className="ai-clarification-options">
          {message.options.map((opt) => (
            <Button
              key={opt.id}
              size="small"
              color="primary"
              fill="outline"
              onClick={() => onSelect(opt.id)}
              className="ai-clarification-option"
            >
              {opt.label}
            </Button>
          ))}
        </div>
      )}
    </div>
  </div>
);

export default ClarificationBubble;
