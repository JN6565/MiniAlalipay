import React, { useState, useCallback, useRef } from 'react';
import { history } from 'umi';
import { Button, Toast } from 'antd-mobile';
import { useSession } from './hooks/useSession';
import { useSSEStream } from './hooks/useSSEStream';
import { Message, UserMessage, AssistantTextMessage, ClarificationMessage, ConfirmationMessage } from './types';
import MessageList from './components/MessageList';
import InputBar from './components/InputBar';
import StreamingBubble from './components/StreamingBubble';
import ClarificationBubble from './components/ClarificationBubble';
import ConfirmationCard from './components/ConfirmationCard';
import './index.less';

let idCounter = 0;
function nextId(): string {
  return `msg_${Date.now()}_${++idCounter}`;
}

const AITalkPage: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const { sessionId, saveSessionId } = useSession();
  const { startStream, abort, streaming } = useSSEStream();
  const currentAssistantIdRef = useRef<string | null>(null);

  /** 发送消息的核心逻辑（通过 SSE 流） */
  const sendViaStream = useCallback(
    async (userContent: string) => {
      const clientMessageId = `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      // 创建待填充的 assistant text bubble
      const assistantId = nextId();
      currentAssistantIdRef.current = assistantId;
      const assistantMsg: AssistantTextMessage = {
        id: assistantId,
        role: 'assistant',
        kind: 'text',
        content: '',
        streaming: true,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, assistantMsg]);

      await startStream(
        { clientMessageId, sessionId: sessionId || undefined, content: userContent },
        {
          'agent-status': (data) => {
            console.debug('SSE status:', data.stage, data.message);
          },

          'agent-tool-call': (data) => {
            if (data.status === 'rejected') {
              Toast.show({ content: `操作被拒绝: ${data.tool}`, duration: 2000 });
            }
          },

          'agent-tool-result': (data) => {
            console.debug('SSE tool result:', data.tool, data.status, data.summary);
          },

          'agent-content': (data) => {
            setMessages((prev) => {
              const updated = [...prev];
              const idx = updated.findIndex((m) => m.id === assistantId);
              if (idx >= 0) {
                const current = updated[idx] as AssistantTextMessage;
                updated[idx] = { ...current, content: current.content + data.delta };
              }
              return updated;
            });
          },

          'agent-confirmation': (data) => {
            setMessages((prev) => [
              ...prev,
              {
                id: nextId(),
                role: 'assistant' as const,
                kind: 'confirmation' as const,
                cardType: data.cardType as 'transfer' | 'credit-repayment',
                draftId: data.draftId,
                summary: data.summary,
                payeeNickname: data.payeeNickname,
                amountFen: data.amountFen,
                status: 'pending' as const,
                timestamp: new Date(),
              } as ConfirmationMessage,
            ]);
          },

          'agent-clarification': (data) => {
            setMessages((prev) => [
              ...prev,
              {
                id: nextId(),
                role: 'assistant' as const,
                kind: 'clarification' as const,
                question: data.question,
                options: data.options || [],
                timestamp: new Date(),
              } as ClarificationMessage,
            ]);
          },

          'agent-done': (data) => {
            if (data.sessionId) saveSessionId(data.sessionId);
            setMessages((prev) => {
              const updated = [...prev];
              const idx = updated.findIndex((m) => m.id === assistantId);
              if (idx >= 0) {
                updated[idx] = { ...updated[idx], streaming: false } as AssistantTextMessage;
              }
              return updated;
            });
          },

          'agent-error': (data) => {
            Toast.show({ content: data.message || 'AI 请求失败' });
            setMessages((prev) => {
              const updated = [...prev];
              const idx = updated.findIndex((m) => m.id === assistantId);
              if (idx >= 0) {
                (updated[idx] as AssistantTextMessage).streaming = false;
              }
              return updated;
            });
          },
        },
      );
    },
    [sessionId, saveSessionId, startStream],
  );

  /** 输入栏发送 */
  const handleSend = useCallback(async () => {
    if (!inputValue.trim()) return;

    const userMsg: UserMessage = {
      id: nextId(),
      role: 'user',
      content: inputValue,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMsg]);
    const currentInput = inputValue;
    setInputValue('');

    await sendViaStream(currentInput);
  }, [inputValue, sendViaStream]);

  /** 澄清选项点击 -- 将选项内容作为用户消息发送 */
  const handleClarificationSelect = useCallback(
    (optionId: string) => {
      // 找到对应选项的 label
      const lastClarification = [...messages]
        .reverse()
        .find((m): m is ClarificationMessage => m.role === 'assistant' && m.kind === 'clarification');
      const option = lastClarification?.options.find((o) => o.id === optionId);
      const displayText = option?.label || optionId;

      const userMsg: UserMessage = {
        id: nextId(),
        role: 'user',
        content: displayText,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, userMsg]);
      sendViaStream(optionId);
    },
    [messages, sendViaStream],
  );

  /** 确认卡片提交 */
  const handleConfirm = useCallback(
    async (draftId: string, password: string) => {
      const confirmContent = `确认提交草稿 ${draftId}`;
      const userMsg: UserMessage = {
        id: nextId(),
        role: 'user',
        content: confirmContent,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, userMsg]);
      await sendViaStream(confirmContent);
    },
    [sendViaStream],
  );

  /** 确认卡片取消 */
  const handleCancel = useCallback((draftId: string) => {
    setMessages((prev) =>
      prev.map((m) => {
        if (m.role === 'assistant' && m.kind === 'confirmation' && m.draftId === draftId) {
          return { ...m, status: 'done', summary: m.summary + ' (已取消)' } as ConfirmationMessage;
        }
        return m;
      }),
    );
  }, []);

  /** 停止生成 */
  const handleStopGeneration = useCallback(() => {
    abort();
  }, [abort]);

  /** 消息渲染分发 */
  const renderMessage = useCallback(
    (msg: Message, _index: number) => {
      if (msg.role === 'user') {
        return (
          <div className="ai-message ai-message-user">
            <div className="ai-message-content">{msg.content}</div>
          </div>
        );
      }
      if (msg.kind === 'text') {
        return <StreamingBubble message={msg} />;
      }
      if (msg.kind === 'clarification') {
        return <ClarificationBubble message={msg} onSelect={handleClarificationSelect} />;
      }
      if (msg.kind === 'confirmation') {
        return <ConfirmationCard message={msg} onConfirm={handleConfirm} onCancel={handleCancel} />;
      }
      return null;
    },
    [handleClarificationSelect, handleConfirm, handleCancel],
  );

  return (
    <div className="ai-talk-page">
      <MessageList messages={messages} renderMessage={renderMessage} />

      <div className="ai-input-area">
        {streaming && (
          <Button size="mini" color="danger" onClick={handleStopGeneration}>
            停止
          </Button>
        )}
        <InputBar
          value={inputValue}
          onChange={setInputValue}
          onSend={handleSend}
          loading={streaming}
          disabled={!inputValue.trim() || streaming}
        />
      </div>

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab" onClick={() => history.push('/h5/home')}>
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab on">
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/contacts')}>
          <span className="tab-icon">👥</span>
          <span className="tab-label">联系人</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/profile')}>
          <span className="tab-icon">👤</span>
          <span className="tab-label">我的</span>
        </div>
      </div>
    </div>
  );
};

export default AITalkPage;
