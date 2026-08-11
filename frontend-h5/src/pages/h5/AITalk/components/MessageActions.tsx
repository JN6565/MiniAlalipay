import React, { useState, useCallback } from 'react';
import { Toast } from 'antd-mobile';
import { IconSet } from '@/components/h5/common';

/**
 * 消息操作按钮栏（参考 DeepSeek 风格）。
 *
 * <p>提供复制、点赞、点踩、重新生成四个能力。重新生成由父组件接管，
 * 本组件只负责回调上抛与点赞/点踩互斥状态。</p>
 */
interface Props {
  /** 当前回复内容（用于复制） */
  content: string;
  /** 当前反馈状态：like / dislike / null */
  feedback: 'like' | 'dislike' | null;
  /** 反馈变更回调：null 表示取消 */
  onFeedbackChange: (next: 'like' | 'dislike' | null) => void;
  /** 重新生成回调（点击时触发） */
  onRegenerate: () => void;
  /** 是否处于禁用态（例如流式中） */
  disabled?: boolean;
}

const MessageActions: React.FC<Props> = ({
  content,
  feedback,
  onFeedbackChange,
  onRegenerate,
  disabled,
}) => {
  const [copied, setCopied] = useState(false);

  /** 复制整段文本到剪贴板。失败时给出兜底提示 */
  const handleCopy = useCallback(async () => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(content);
      } else {
        // 兼容非安全上下文：临时 textarea + execCommand
        const ta = document.createElement('textarea');
        ta.value = content;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      setCopied(true);
      Toast.show({ icon: 'success', content: '已复制' });
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      Toast.show({ icon: 'fail', content: '复制失败' });
    }
  }, [content]);

  /** 点赞/点踩互斥：再次点击同一项则取消 */
  const handleToggleFeedback = useCallback(
    (target: 'like' | 'dislike') => {
      const next = feedback === target ? null : target;
      onFeedbackChange(next);
    },
    [feedback, onFeedbackChange],
  );

  return (
    <div className="ai-message-actions">
      <button
        type="button"
        className={`ai-action-btn${copied ? ' ai-action-btn--active' : ''}`}
        onClick={handleCopy}
        disabled={disabled}
        title="复制"
        aria-label="复制"
      >
        {copied ? <IconSet name="check" size={14} /> : <IconSet name="copy" size={14} />}
      </button>
      <button
        type="button"
        className="ai-action-btn"
        onClick={onRegenerate}
        disabled={disabled}
        title="重新生成"
        aria-label="重新生成"
      >
        <IconSet name="refresh" size={14} />
      </button>
      <button
        type="button"
        className={`ai-action-btn${feedback === 'like' ? ' ai-action-btn--active' : ''}`}
        onClick={() => handleToggleFeedback('like')}
        disabled={disabled}
        title="有帮助"
        aria-label="有帮助"
      >
        <IconSet name="thumbUp" size={14} />
      </button>
      <button
        type="button"
        className={`ai-action-btn${feedback === 'dislike' ? ' ai-action-btn--active' : ''}`}
        onClick={() => handleToggleFeedback('dislike')}
        disabled={disabled}
        title="没帮助"
        aria-label="没帮助"
      >
        <IconSet name="thumbDown" size={14} />
      </button>
    </div>
  );
};

export default MessageActions;