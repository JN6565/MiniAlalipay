import React, { useState, useCallback, useRef } from 'react';
import { history } from 'umi';
import dayjs from 'dayjs';
import { useSession } from './hooks/useSession';
import { useSSEStream } from './hooks/useSSEStream';
import {
  Message,
  UserMessage,
  AssistantTextMessage,
  ClarificationMessage,
  AssistantErrorMessage,
  ToolResultMessage,
  ConfirmationMessage,
} from './types';
import MessageList from './components/MessageList';
import InputBar, { InputFeatures } from './components/InputBar';
import ClarificationBubble from './components/ClarificationBubble';
import StreamingBubble from './components/StreamingBubble';
import ToolResultCard from './components/ToolResultCard';
import ConfirmationCard from './components/ConfirmationCard';
import SessionList from './components/SessionList';
import MarkdownContent from './components/MarkdownContent';
import MessageActions from './components/MessageActions';
import { confirmSubmission } from '@/services/ai';
import './index.less';

let idCounter = 0;
function nextId(): string {
  return `local_${Date.now()}_${++idCounter}`;
}
function nextClientMessageId(): string {
  const rand =
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID().replace(/-/g, '').slice(0, 12)
      : Math.random().toString(36).slice(2, 14);
  return `msg_${Date.now()}_${rand}`;
}

/** 推断消息所属分组的标题文本（基于 lastActiveAt） */
function deriveSessionTitle(text: string): string {
  const trimmed = text.trim();
  if (!trimmed) return '新对话';
  // 截取前 18 个字符作为标题（DeepSeek 行为）
  return trimmed.length > 18 ? `${trimmed.slice(0, 18)}…` : trimmed;
}

const AITalkPage: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [sessionDrawerOpen, setSessionDrawerOpen] = useState(false);
  /** 当前会话标题（用于顶部居中展示），新建会话时显示"新对话" */
  const [sessionTitle, setSessionTitle] = useState('新对话');
  /** 输入栏功能开关（深度思考 / 智能搜索） */
  const [features, setFeatures] = useState<InputFeatures>({});

  const {
    sessionId,
    sessions,
    sessionsLoading,
    saveSessionId,
    clearSession,
    loadSessions,
    switchSession,
  } = useSession();

  const streamingMsgIdRef = useRef<string | null>(null);
  const streamBufferRef = useRef('');
  /** 记录最后一条用户消息内容，用于错误重试时恢复 */
  const lastUserContentRef = useRef('');

  /** 流式回调：文本增量 */
  const handleContentDelta = useCallback((delta: string) => {
    streamBufferRef.current += delta;
    const fullContent = streamBufferRef.current;
    setMessages((prev) =>
      prev.map((m) =>
        m.id === streamingMsgIdRef.current && m.role === 'assistant' && m.kind === 'text'
          ? { ...m, content: fullContent }
          : m,
      ),
    );
  }, []);

  /** 流式回调：工具调用开始 → 消息列表中插入加载卡片 */
  const handleToolCall = useCallback((toolName: string) => {
    const cardId = nextId();
    streamingMsgIdRef.current = cardId;
    const cardMsg: ToolResultMessage = {
      id: cardId,
      role: 'assistant',
      kind: 'tool-result',
      tool: toolName,
      status: 'running',
      summary: '',
      data: {},
      loading: true,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, cardMsg]);
  }, []);

  /** 流式回调：工具调用完成 → 替换卡片内容 */
  const handleToolResult = useCallback(
    (toolName: string, status: string, summary: string, data: Record<string, any>) => {
      const id = streamingMsgIdRef.current;
      if (!id) return;

      // GAP-7：prepare_confirmation_card 工具返回后，将消息转换为确认卡片
      if (toolName === 'prepare_confirmation_card' && status === 'success') {
        const confirmMsg: ConfirmationMessage = {
          id,
          role: 'assistant',
          kind: 'confirmation',
          cardType: data.cardType === 'repay' ? 'repay' : 'transfer',
          draftId: data.draftId || '',
          payeeOptions: data.payeeNickname
            ? [{ id: data.payeeId || '', label: String(data.payeeNickname) }]
            : [],
          amountFen: typeof data.amountFen === 'number' ? data.amountFen : undefined,
          note: summary,
          status: 'pending',
          timestamp: new Date(),
        };
        setMessages((prev) =>
          prev.map((m) => (m.id === id ? confirmMsg : m)),
        );
        // 确认卡片后不创建文本占位符，等待用户操作
        return;
      }

      setMessages((prev) =>
        prev.map((m) =>
          m.id === id && m.role === 'assistant' && m.kind === 'tool-result'
            ? { ...m, tool: toolName, status, summary, data, loading: false }
            : m,
        ),
      );
      // tool-result 之后，文本增量会落到下一个 text 消息
      const textId = nextId();
      streamingMsgIdRef.current = textId;
      streamBufferRef.current = '';
      const textMsg: AssistantTextMessage = {
        id: textId,
        role: 'assistant',
        kind: 'text',
        content: '',
        streaming: true,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, textMsg]);
    },
    [],
  );

  /** 流式回调：澄清事件 */
  const handleClarification = useCallback(
    (question: string, options: { id: string; label: string }[]) => {
      const id = streamingMsgIdRef.current;
      if (id) {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id
              ? ({
                  id,
                  role: 'assistant' as const,
                  kind: 'clarification' as const,
                  question,
                  options,
                  timestamp: new Date(),
                } as ClarificationMessage)
              : m,
          ),
        );
      }
    },
    [],
  );

  /** 流式回调：完成 */
  const handleDone = useCallback(
    (newSessionId: string, _messageId: string, _intent: string) => {
      if (newSessionId) saveSessionId(newSessionId);
      const id = streamingMsgIdRef.current;
      if (id) {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id && m.role === 'assistant' && m.kind === 'text'
              ? { ...m, streaming: false, showActions: true }
              : m,
          ),
        );
      }
      streamingMsgIdRef.current = null;
      streamBufferRef.current = '';
    },
    [saveSessionId],
  );

  /** 流式回调：错误 */
  const handleStreamError = useCallback((message: string) => {
    const id = streamingMsgIdRef.current;
    const content = streamBufferRef.current;
    if (id) {
      if (content) {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id && m.role === 'assistant' && m.kind === 'text'
              ? { ...m, streaming: false }
              : m,
          ),
        );
      } else {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id
              ? ({
                  id,
                  role: 'assistant' as const,
                  kind: 'error' as const,
                  content: message,
                  retryContent: lastUserContentRef.current,
                  timestamp: new Date(),
                } as AssistantErrorMessage)
              : m,
          ),
        );
      }
    }
    streamingMsgIdRef.current = null;
    streamBufferRef.current = '';
  }, []);

  const { streaming, startStream, cancelStream } = useSSEStream({
    onContentDelta: handleContentDelta,
    onClarification: handleClarification,
    onToolCall: handleToolCall,
    onToolResult: handleToolResult,
    onDone: handleDone,
    onError: handleStreamError,
  });

  /** 核心发送逻辑 */
  const doSend = useCallback(
    async (rawContent: string) => {
      const content = rawContent.trim();
      if (!content || streaming) return;

      // 记录用户原文，供错误重试时复用
      lastUserContentRef.current = content;

      const userMsg: UserMessage = {
        id: nextId(),
        role: 'user',
        content,
        timestamp: new Date(),
      };

      // 创建流式占位符
      const assistantMsgId = nextId();
      const assistantMsg: AssistantTextMessage = {
        id: assistantMsgId,
        role: 'assistant',
        kind: 'text',
        content: '',
        streaming: true,
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, userMsg, assistantMsg]);
      streamingMsgIdRef.current = assistantMsgId;
      streamBufferRef.current = '';

      // 首条消息时更新顶部会话标题
      if (messages.length === 0) {
        setSessionTitle(deriveSessionTitle(content));
      }

      startStream({
        clientMessageId: nextClientMessageId(),
        sessionId: sessionId || undefined,
        content,
      });
    },
    [streaming, sessionId, startStream, messages.length],
  );

  const handleSend = useCallback(() => {
    const text = inputValue;
    setInputValue('');
    doSend(text);
  }, [inputValue, doSend]);

  const handleClarificationSelect = useCallback(
    (optionId: string) => {
      const lastClarification = [...messages]
        .reverse()
        .find((m): m is ClarificationMessage => m.role === 'assistant' && m.kind === 'clarification');
      const option = lastClarification?.options.find((o) => o.id === optionId);
      doSend(option?.label || optionId);
    },
    [messages, doSend],
  );

  const handleRetry = useCallback(
    (errorMsg: AssistantErrorMessage) => {
      setMessages((prev) => prev.filter((m) => m.id !== errorMsg.id));
      doSend(errorMsg.retryContent);
    },
    [doSend],
  );

  /** 点赞/点踩回调：更新消息 feedback 字段（仅 UI 状态，未上报后端） */
  const handleFeedbackChange = useCallback(
    (msgId: string, next: 'like' | 'dislike' | null) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === msgId && m.role === 'assistant' && m.kind === 'text'
            ? { ...m, feedback: next }
            : m,
        ),
      );
    },
    [],
  );

  /** 确认卡片——用户点击确认并完成支付密码输入后触发 */
  const handleConfirmTransfer = useCallback(
    async (draftId: string, payeeId: string, amountFen: number, password: string) => {
      try {
        const idempotencyKey = `confirm_${draftId}_${Date.now()}`;
        await confirmSubmission(draftId, password, idempotencyKey);
        // 更新卡片状态为已完成
        setMessages((prev) =>
          prev.map((m) =>
            m.id === streamingMsgIdRef.current && m.kind === 'confirmation'
              ? { ...m, status: 'done' as const }
              : m,
          ),
        );
        // 插入成功提示消息
        const successId = nextId();
        const successMsg: AssistantTextMessage = {
          id: successId,
          role: 'assistant',
          kind: 'text',
          content: '✅ 转账已提交，请稍后查询交易状态确认结果。',
          streaming: false,
          showActions: true,
          timestamp: new Date(),
        };
        setMessages((prev) => [...prev, successMsg]);
      } catch (err: any) {
        const errorMsg: AssistantErrorMessage = {
          id: nextId(),
          role: 'assistant',
          kind: 'error',
          content: err?.message || '转账确认失败，请稍后重试。',
          retryContent: '',
          timestamp: new Date(),
        };
        setMessages((prev) => [...prev, errorMsg]);
      }
    },
    [],
  );

  /** 确认卡片——用户取消操作 */
  const handleCancelTransfer = useCallback(
    (draftId: string) => {
      // 更新卡片状态为已取消
      setMessages((prev) =>
        prev.map((m) =>
          m.kind === 'confirmation' && m.draftId === draftId
            ? { ...m, status: 'cancelled' as const }
            : m,
        ),
      );
      const cancelMsg: AssistantTextMessage = {
        id: nextId(),
        role: 'assistant',
        kind: 'text',
        content: '已取消本次操作。',
        streaming: false,
        showActions: true,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, cancelMsg]);
    },
    [],
  );

  /** 重新生成：复用最后一条用户消息内容再次发送 */
  const handleRegenerate = useCallback(
    (msgId: string) => {
      if (streaming) return;
      // 找到当前 AI 消息前最近一条用户消息
      const idx = messages.findIndex((m) => m.id === msgId);
      if (idx < 0) return;
      let prevUser: UserMessage | undefined;
      for (let i = idx - 1; i >= 0; i--) {
        if (messages[i].role === 'user') {
          prevUser = messages[i] as UserMessage;
          break;
        }
      }
      if (!prevUser) return;
      // 移除当前 AI 消息及其后所有内容，复用原内容再发送
      setMessages((prev) => prev.slice(0, idx));
      doSend(prevUser.content);
    },
    [messages, streaming, doSend],
  );

  /** 新建会话 */
  const handleNewSession = useCallback(() => {
    clearSession();
    setMessages([]);
    setSessionTitle('新对话');
  }, [clearSession]);

  /** 打开历史会话抽屉 */
  const handleOpenSessions = useCallback(async () => {
    setSessionDrawerOpen(true);
    await loadSessions();
  }, [loadSessions]);

  /** 切换到历史会话 */
  const handleSelectSession = useCallback(
    async (targetSessionId: string) => {
      const historyMessages = await switchSession(targetSessionId);
      if (historyMessages.length > 0) {
        setMessages(historyMessages);
        // 用首条用户消息作为会话标题
        const firstUser = historyMessages.find((m) => m.role === 'user');
        if (firstUser && firstUser.role === 'user') {
          setSessionTitle(deriveSessionTitle(firstUser.content));
        }
      }
    },
    [switchSession],
  );

  /** 渲染分发 */
  const renderMessage = useCallback(
    (msg: Message) => {
      // 用户消息：右侧紧凑气泡
      if (msg.role === 'user') {
        return (
          <div className="ai-message ai-message-user">
            <div className="ai-message-body">
              <div className="ai-message-content">{msg.content}</div>
            </div>
          </div>
        );
      }

      // 工具结果卡片
      if (msg.kind === 'tool-result') {
        return (
          <div className="ai-message ai-message-assistant">
            <div className="ai-message-body">
              <ToolResultCard message={msg} />
            </div>
          </div>
        );
      }

      // GAP-7：确认卡片（转账/还款二次确认）
      if (msg.kind === 'confirmation') {
        return (
          <ConfirmationCard
            message={msg}
            onConfirm={handleConfirmTransfer}
            onCancel={handleCancelTransfer}
          />
        );
      }

      // 文本消息（含流式中）
      if (msg.kind === 'text') {
        return (
          <StreamingBubble
            message={msg}
            onFeedbackChange={handleFeedbackChange}
            onRegenerate={handleRegenerate}
          />
        );
      }

      // 澄清消息
      if (msg.kind === 'clarification') {
        return <ClarificationBubble message={msg} onSelect={handleClarificationSelect} />;
      }

      // 错误消息
      if (msg.kind === 'error') {
        return (
          <div className="ai-message ai-message-assistant">
            <div className="ai-message-body">
              <div className="ai-message-content ai-error">
                <span className="ai-error-icon">⚠️</span>
                <span className="ai-error-text">{msg.content}</span>
                <button type="button" className="ai-retry-btn" onClick={() => handleRetry(msg)}>
                  重试
                </button>
              </div>
              <MessageActions
                content={msg.content}
                feedback={null}
                onFeedbackChange={() => {
                  /* 错误消息不参与点赞点踩 */
                }}
                onRegenerate={() => handleRetry(msg)}
              />
            </div>
          </div>
        );
      }

      return null;
    },
    [handleClarificationSelect, handleRetry, handleFeedbackChange, handleRegenerate, handleConfirmTransfer, handleCancelTransfer],
  );

  return (
    <div className="ai-talk-page">
      {/* 顶部操作栏（参考 DeepSeek：菜单 + 标题 + 新对话） */}
      <div className="ai-top-bar">
        <button
          type="button"
          className="ai-top-icon-btn"
          onClick={handleOpenSessions}
          aria-label="打开历史会话"
        >
          ☰
        </button>
        <div className="ai-top-title-block">
          <div className="ai-top-title">{sessionTitle}</div>
          <div className="ai-top-subtitle">
            {features.deepThinking ? '深度思考模式' : '快速模式'}
          </div>
        </div>
        <button
          type="button"
          className="ai-top-icon-btn"
          onClick={handleNewSession}
          aria-label="新建对话"
        >
          ✚
        </button>
        {/* GAP-4：一键切换到传统表单模式，AI 与表单共享底层接口 */}
        <button
          type="button"
          className="ai-top-icon-btn ai-form-switch-btn"
          onClick={() => history.push('/h5/transfer')}
          aria-label="切换到表单模式"
          title="使用传统表单操作"
        >
          ✎
        </button>
      </div>

      <MessageList
        messages={messages}
        renderMessage={renderMessage}
        onSuggestionClick={doSend}
      />

      {/* 底部输入区：固定定位 */}
      <div className="ai-input-area">
        <InputBar
          value={inputValue}
          onChange={setInputValue}
          onSend={handleSend}
          loading={streaming}
          disabled={!inputValue.trim() || streaming}
          features={features}
          onFeaturesChange={setFeatures}
        />
      </div>

      {/* 历史会话抽屉 */}
      <SessionList
        visible={sessionDrawerOpen}
        onClose={() => setSessionDrawerOpen(false)}
        sessions={sessions}
        currentSessionId={sessionId}
        loading={sessionsLoading}
        onSelect={handleSelectSession}
        onNewSession={() => {
          handleNewSession();
          setSessionDrawerOpen(false);
        }}
      />
    </div>
  );
};

export default AITalkPage;