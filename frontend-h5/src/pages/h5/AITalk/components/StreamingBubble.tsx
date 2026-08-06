import React from 'react';
import { AssistantTextMessage } from '../types';

interface Props {
  message: AssistantTextMessage;
}

const StreamingBubble: React.FC<Props> = ({ message }) => (
  <div className="ai-message ai-message-assistant">
    <div className={`ai-message-content ${message.streaming ? 'ai-streaming' : ''}`}>
      {message.content || '...'}
      {message.streaming && <span className="ai-cursor">|</span>}
    </div>
  </div>
);

export default StreamingBubble;
