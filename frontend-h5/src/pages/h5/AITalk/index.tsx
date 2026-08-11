import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useSession } from './hooks/useSession';
import { useSSEStream } from './hooks/useSSEStream';
import {
  Message,
  UserMessage,
  AssistantTextMessage,
  ClarificationMessage,
  AssistantErrorMessage,
} from './types';
import MessageList from './components/MessageList';
import InputBar from './components/InputBar';
import ClarificationBubble from './components/ClarificationBubble';
import StreamingBubble from './components/StreamingBubble';
import SessionList from './components/SessionList';
import MessageActions from './components/MessageActions';
import { confirmSubmission, sendMessage } from '@/services/ai';
import { IconSet } from '@/components/h5/common';
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
  /** 当前会话标题（用于顶部居中展示），新建会话时显示“新对话” */
  const [sessionTitle, setSessionTitle] = useState('新对话');

  const {
    sessionId,
    sessions,
    sessionsLoading,
    saveSessionId,
    clearSession,
    loadSessions,
    switchSession,
    removeSession,
    renameSessionTitle,
  } = useSession();

  const streamingMsgIdRef = useRef<string | null>(null);
  const streamBufferRef = useRef('');
  /** 记录最后一条用户消息内容，用于错误重试时恢复 */
  const lastUserContentRef = useRef('');
  /** 记录最近的收款人搜索结果，供确认卡片展示手机号 */
  const lastPayeesRef = useRef<any[]>([]);

  /**
   * 组件挂载时恢复会话：如果 localStorage 中有 sessionId，
   * 自动加载历史消息，避免页面切换后丢失对话上下文。
   */
  useEffect(() => {
    if (sessionId) {
      switchSession(sessionId).then((historyMessages) => {
        if (historyMessages.length > 0) {
          setMessages(historyMessages);
          // 用首条用户消息作为会话标题
          const firstUser = historyMessages.find((m) => m.role === 'user');
          if (firstUser && firstUser.role === 'user') {
            setSessionTitle(deriveSessionTitle(firstUser.content));
          }
        }
      });
    }
    // 仅在挂载时执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 流式回调：文本增量 */
  const handleContentDelta = useCallback((delta: string) => {
    streamBufferRef.current += delta;
    const fullContent = streamBufferRef.current;
    setMessages((prev) =>
      prev.map((m) =>
        m.id === streamingMsgIdRef.current && m.role === 'assistant' && m.kind === 'text'
          ? { ...m, content: fullContent, streaming: true }
          : m,
      ),
    );
  }, []);

  /** 转账流程中间步骤工具（不展示工具结果卡片，只捕获数据供确认卡片使用） */
  const TRANSFER_INTERMEDIATE_TOOLS = new Set([
    'search_payees',
    'create_transfer_draft',
    'validate_transfer_draft',
  ]);

  /** 流式回调：工具调用开始 → 创建空文本消息作为加载占位符 */
  const handleToolCall = useCallback((toolName: string) => {
    // 中间步骤工具不展示加载卡片，避免对话被大量卡片中断
    if (TRANSFER_INTERMEDIATE_TOOLS.has(toolName)) {
      return;
    }
    // 如果当前消息尚无文本内容（空占位符或仅有卡片），直接复用，避免产生多余空气泡
    if (streamingMsgIdRef.current && !streamBufferRef.current) {
      return;
    }
    // 创建空文本消息作为加载占位符，工具结果将内嵌到此消息中
    const msgId = nextId();
    streamingMsgIdRef.current = msgId;
    const loadingMsg: AssistantTextMessage = {
      id: msgId,
      role: 'assistant',
      kind: 'text',
      content: '',
      streaming: true,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, loadingMsg]);
  }, []);

  /** 流式回调：工具调用完成 → 将工具结果内嵌到文本消息中 */
  const handleToolResult = useCallback(
    (toolName: string, status: string, summary: string, data: Record<string, any>) => {
      // 捕获 search_payees 结果，供后续确认卡片使用（必须在 id 检查之前，因为中间工具不设置 id）
      if (toolName === 'search_payees' && status === 'success') {
        lastPayeesRef.current = data.users || [];
      }

      // 转账流程中间步骤：不展示工具结果，仅捕获数据
      // 不清空 streamingMsgIdRef，让后续 LLM 文本内容继续流入文本气泡
      if (TRANSFER_INTERMEDIATE_TOOLS.has(toolName)) {
        return;
      }

      const id = streamingMsgIdRef.current;
      if (!id) return;

      // prepare_confirmation_card：将确认卡片内嵌到占位文本消息中
      if (toolName === 'prepare_confirmation_card' && status === 'success') {
        const payees = lastPayeesRef.current || [];
        const payeeId = data.payeeId || '';

        // 更新占位消息，内嵌确认卡片
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id
              ? {
                  ...m,
                  streaming: false,
                  confirmationCard: {
                    cardType: data.cardType === 'repay' ? 'repay' : 'transfer',
                    draftId: data.draftId || '',
                    version: typeof data.version === 'number' ? data.version : undefined,
                    payeeOptions: payees.length > 0
                      ? payees.map((p: any) => ({
                          id: p.userId || '',
                          label: `${p.nickname || ''} (${p.maskedPhone || p.phoneTail || ''})`,
                          maskedPhone: p.maskedPhone || '',
                          phoneTail: p.phoneTail || '',
                        }))
                      : (data.payeeNickname
                          ? [{ id: payeeId, label: String(data.payeeNickname), maskedPhone: '', phoneTail: '' }]
                          : []),
                    amountFen: typeof data.amountFen === 'number' ? data.amountFen : undefined,
                    note: summary,
                    status: 'pending' as const,
                  },
                }
              : m,
          ),
        );
        // 不创建新文本消息：后续 LLM 文本将追加到卡片消息，实现文本+卡片同气泡
        return;
      }

      // 其他工具：将工具结果卡片累积到占位消息中（支持多工具结果同气泡）
      setMessages((prev) =>
        prev.map((m) => {
          if (m.id !== id || m.role !== 'assistant' || m.kind !== 'text') return m;
          const existing = (m as AssistantTextMessage).toolResultCards ?? [];
          return {
            ...m,
            streaming: false,
            toolResultCards: [...existing, { tool: toolName, status, summary, data }],
          };
        }),
      );
      // 不创建新文本消息：后续 LLM 文本将追加到卡片消息，实现文本+卡片同气泡
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
      // 清理末尾的空文本消息（工具调用后 LLM 未产生文本时残留的占位符）
      setMessages((prev) => {
        let end = prev.length;
        while (end > 0) {
          const last = prev[end - 1];
          if (
            last.role === 'assistant' &&
            (last as AssistantTextMessage).kind === 'text' &&
            !(last as AssistantTextMessage).content &&
            !(last as AssistantTextMessage).toolResultCards &&
            !(last as AssistantTextMessage).confirmationCard
          ) {
            end--;
          } else {
            break;
          }
        }
        return end < prev.length ? prev.slice(0, end) : prev;
      });
    },
    [saveSessionId],
  );

  /** 流式回调：错误 */
  const handleStreamError = useCallback((message: string) => {
    const id = streamingMsgIdRef.current;
    const content = streamBufferRef.current;
    if (id) {
      // 检查当前消息是否已有卡片（有卡片时保留，不转为错误消息）
      let hasCards = false;
      setMessages((prev) => {
        const cur = prev.find((m) => m.id === id);
        if (cur && cur.role === 'assistant' && cur.kind === 'text') {
          hasCards = !!((cur as AssistantTextMessage).toolResultCards?.length || (cur as AssistantTextMessage).confirmationCard);
        }
        return prev;
      });
      if (content || hasCards) {
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
    async (draftId: string, payeeId: string, amountFen: number, password: string, version?: number) => {
      try {
        const idempotencyKey = `confirm_${draftId}_${Date.now()}`;
        const transferResult = await confirmSubmission(draftId, password, version ?? 0, idempotencyKey);
        // 更新内嵌卡片状态为已完成（确认卡片现在嵌入在文本消息中）
        setMessages((prev) =>
          prev.map((m) =>
            'kind' in m && m.kind === 'text' && m.confirmationCard?.draftId === draftId
              ? { ...m, confirmationCard: { ...m.confirmationCard, status: 'done' as const } }
              : m,
          ),
        );
        // 插入成功提示消息
        const successId = nextId();
        const successMsg: AssistantTextMessage = {
          id: successId,
          role: 'assistant',
          kind: 'text',
          content: '转账已提交，请稍后查询交易状态确认结果。',
          streaming: false,
          showActions: true,
          timestamp: new Date(),
        };
        setMessages((prev) => [...prev, successMsg]);

        // 通知 AI agent 转账已提交，更新其上下文（不显示在 UI 中）
        if (transferResult?.transactionId && sessionId) {
          try {
            await sendMessage({
              clientMessageId: `notify_transfer_${transferResult.transactionId}_${Date.now()}`,
              sessionId,
              content: `[系统通知] 转账已确认提交：交易ID=${transferResult.transactionId}，状态=${transferResult.status || 'PROCESSING'}，金额=${(amountFen / 100).toFixed(2)}元，收款人ID=${payeeId}。`,
            });
          } catch (e) {
            console.warn('通知 AI agent 转账结果失败:', e);
          }
        }
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
    [sessionId],
  );

  /** 确认卡片——用户取消操作 */
  const handleCancelTransfer = useCallback(
    (draftId: string) => {
      // 更新内嵌卡片状态为已取消（确认卡片现在嵌入在文本消息中）
      setMessages((prev) =>
        prev.map((m) =>
          'kind' in m && m.kind === 'text' && m.confirmationCard?.draftId === draftId
            ? { ...m, confirmationCard: { ...m.confirmationCard, status: 'cancelled' as const } }
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

  /** 删除会话 */
  const handleDeleteSession = useCallback(
    async (targetSessionId: string) => {
      await removeSession(targetSessionId);
      // 如果删除的是当前会话，清空并回到新对话状态
      if (targetSessionId === sessionId) {
        clearSession();
        setMessages([]);
        setSessionTitle('新对话');
      }
    },
    [removeSession, sessionId, clearSession],
  );

  /** 重命名会话 */
  const handleRenameSession = useCallback(
    async (targetSessionId: string, newTitle: string) => {
      await renameSessionTitle(targetSessionId, newTitle);
      // 如果重命名的是当前会话，更新顶部标题
      if (targetSessionId === sessionId) {
        setSessionTitle(newTitle);
      }
    },
    [renameSessionTitle, sessionId],
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

      // 工具结果卡片（已内嵌到文本消息中，不再作为独立气泡渲染）

      // 文本消息（含流式中）
      if (msg.kind === 'text') {
        return (
          <StreamingBubble
            message={msg}
            onFeedbackChange={handleFeedbackChange}
            onRegenerate={handleRegenerate}
            onConfirmTransfer={handleConfirmTransfer}
            onCancelTransfer={handleCancelTransfer}
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
            <div className="ai-assistant-orb" />
            <div className="ai-message-body">
              <div className="ai-message-content ai-error">
                <span className="ai-error-icon">!</span>
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
          <IconSet name="drawer" size={18} />
        </button>
        <div className="ai-top-title-block">
          <div className="ai-top-title">
            <IconSet name="ai" size={15} color="#8fc2ff" />
            <span className="ai-top-title-text">{sessionTitle}</span>
          </div>
        </div>
        <button
          type="button"
          className="ai-top-icon-btn"
          onClick={handleNewSession}
          aria-label="新建对话"
        >
          <IconSet name="plus" size={18} />
        </button>

      </div>

      <MessageList
        messages={messages}
        renderMessage={renderMessage}
        onSuggestionClick={doSend}
      />

      {/* 底部输入区：绝对定位锚定页面内容底部，随滚动容器末尾展示 */}
      <div className="ai-input-area">
        <InputBar
          value={inputValue}
          onChange={setInputValue}
          onSend={handleSend}
          loading={streaming}
          disabled={!inputValue.trim() || streaming}
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
        onDelete={handleDeleteSession}
        onRename={handleRenameSession}
        onNewSession={() => {
          setSessionDrawerOpen(false);
          handleNewSession();
        }}
      />
    </div>
  );
};

export default AITalkPage;
