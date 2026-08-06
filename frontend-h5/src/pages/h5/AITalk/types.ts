// types.ts — AI Talk 页面消息类型

export interface UserMessage {
  id: string;
  role: 'user';
  content: string;
  timestamp: Date;
}

export interface AssistantTextMessage {
  id: string;
  role: 'assistant';
  kind: 'text';
  content: string;
  streaming: boolean;
  timestamp: Date;
}

export interface ClarificationMessage {
  id: string;
  role: 'assistant';
  kind: 'clarification';
  question: string;
  options: { id: string; label: string }[];
  timestamp: Date;
}

export interface ConfirmationMessage {
  id: string;
  role: 'assistant';
  kind: 'confirmation';
  cardType: 'transfer' | 'credit-repayment';
  draftId: string;
  summary: string;
  payeeNickname?: string;
  amountFen?: number;
  status: 'pending' | 'done';
  timestamp: Date;
}

export type Message =
  | UserMessage
  | AssistantTextMessage
  | ClarificationMessage
  | ConfirmationMessage;

// SSE 事件载荷
export interface StreamHandlers {
  'agent-status': (data: { stage: string; message: string }) => void;
  'agent-tool-call': (data: { tool: string; status: string }) => void;
  'agent-tool-result': (data: { tool: string; status: string; summary: string }) => void;
  'agent-content': (data: { delta: string }) => void;
  'agent-confirmation': (data: {
    cardType: string; draftId: string;
    payeeNickname: string; amountFen: number;
    payeePhoneTail: string; summary: string;
  }) => void;
  'agent-clarification': (data: {
    question: string; options: { id: string; label: string }[];
  }) => void;
  'agent-done': (data: { messageId: string; sessionId: string; intent: string }) => void;
  'agent-error': (data: { code: string; message: string }) => void;
}
