// types.ts — AI Talk 页面消息类型

/** 用户消息 */
export interface UserMessage {
  id: string;
  role: 'user';
  content: string;
  timestamp: Date;
}

/** 助手纯文本 / Markdown 回复（可内嵌确认卡片） */
export interface AssistantTextMessage {
  id: string;
  role: 'assistant';
  kind: 'text';
  content: string;
  streaming: boolean;
  /** 思考过程文本（DeepSeek 风格的"已思考（用时 X 秒）"展开内容），可为空 */
  thinking?: string;
  /** 思考耗时（秒），用于折叠行展示 */
  thinkingSeconds?: number;
  /** 用户对本次回复的反馈：like / dislike / null */
  feedback?: 'like' | 'dislike' | null;
  /** 是否展示操作按钮行（false 时隐藏操作行；欢迎页/历史回放等场景可关闭） */
  showActions?: boolean;
  /** 内嵌的确认卡片数据（转账/还款二次确认），与文本同气泡显示 */
  confirmationCard?: {
    cardType: 'transfer' | 'repay';
    draftId: string;
    version?: number;
    payeeOptions?: { id: string; label: string; maskedPhone?: string; phoneTail?: string }[];
    amountFen?: number;
    note?: string;
    status: 'pending' | 'done' | 'cancelled';
  };
  /** 内嵌的工具结果卡片列表（余额、额度、交易记录等），与文本同气泡显示 */
  toolResultCards?: {
    tool: string;
    status: string;
    summary: string;
    data: Record<string, any>;
  }[];
  timestamp: Date;
}

/** 助手澄清引导消息：options 由后端槽位推导，可为空（允许自由输入） */
export interface ClarificationMessage {
  id: string;
  role: 'assistant';
  kind: 'clarification';
  question: string;
  options: { id: string; label: string }[];
  timestamp: Date;
}

/** 工具结果卡片消息（SSE agent-tool-result 事件产生） */
export interface ToolResultMessage {
  id: string;
  role: 'assistant';
  kind: 'tool-result';
  /** 调用的工具名（如 get_balance、get_credit_summary 等） */
  tool: string;
  /** 执行状态：success | failed */
  status: string;
  /** 自然语言摘要 */
  summary: string;
  /** 后端返回的结构化数据，供卡片渲染 */
  data: Record<string, any>;
  /** 是否正在加载中（tool-call 事件创建时设为 true，tool-result 到达后置为 false） */
  loading: boolean;
  timestamp: Date;
}

/** 助手错误消息：请求失败时留在消息流中，支持一键重试 */
export interface AssistantErrorMessage {
  id: string;
  role: 'assistant';
  kind: 'error';
  content: string;
  retryContent: string;
  timestamp: Date;
}

/**
 * 高风险操作的二次确认消息（转账/还款）。
 *
 * <p>当前 AI 对话流中尚未接入 ConfirmationCard 的渲染（页面层未注册该类型分支），
 * 本接口仅为 ConfirmationCard 组件提供类型契约，便于后续接入时不破坏类型一致性。</p>
 */
export interface ConfirmationMessage {
  id: string;
  role: 'assistant';
  kind: 'confirmation';
  cardType: 'transfer' | 'repay';
  draftId: string;
  version?: number;
  payeeOptions?: { id: string; label: string; maskedPhone?: string; phoneTail?: string }[];
  amountFen?: number;
  note?: string;
  status: 'pending' | 'done' | 'cancelled';
  timestamp: Date;
}

export type Message =
  | UserMessage
  | AssistantTextMessage
  | ClarificationMessage
  | ToolResultMessage
  | AssistantErrorMessage
  | ConfirmationMessage;

/** 会话摘要（会话列表项） */
export interface SessionInfo {
  sessionId: string;
  title: string;
  lastActiveAt: string;
  messageCount: number;
  status: string;
}

/** 历史消息（用于从后端恢复消息列表） */
export interface HistoryMessage {
  messageId: string;
  role: string;
  content: string;
  createdAt: string;
  /** 消息类型：TEXT（文本）/ TOOL_RESULT（工具结果） */
  kind?: string;
  /** 工具名称（仅 TOOL_RESULT 有值） */
  toolName?: string;
}
