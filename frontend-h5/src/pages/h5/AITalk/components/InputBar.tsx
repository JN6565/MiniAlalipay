import React, { KeyboardEvent } from 'react';
import { IconSet } from '@/components/h5/common';

interface Props {
  value: string;
  onChange: (v: string) => void;
  onSend: () => void;
  loading: boolean;
  disabled: boolean;
}

/**
 * 底部输入栏。
 *
 * <p>包含输入框 + 附件按钮 + 发送按钮。支持回车发送、Shift+回车换行。</p>
 */
const InputBar: React.FC<Props> = ({
  value,
  onChange,
  onSend,
  loading,
  disabled,
}) => {

  /** 回车发送；Shift+回车换行（textarea 时生效） */
  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement | HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!disabled && value.trim()) onSend();
    }
  };

  /**
   * 键盘适配：移动端软键盘弹出时可视区域收缩，延迟等滚动容器高度
   * 变化后再把输入框滚入可见区域，避免输入框被键盘遮挡。
   */
  const handleFocus = (e: React.FocusEvent<HTMLTextAreaElement>) => {
    const target = e.target;
    setTimeout(() => {
      target.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }, 300);
  };

  const hasContent = !!value.trim();

  return (
    <div className="ai-input-bar">
      {/* 输入区：附件 + 输入框 + 发送按钮 */}
      <div className="ai-input-row">
        <div className="ai-input-wrapper">
          <textarea
            className="ai-input"
            placeholder="和小智说点什么…"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={handleFocus}
            maxLength={2000}
            rows={1}
            disabled={loading}
          />
        </div>

        <button
          type="button"
          className={`ai-send-btn${hasContent && !loading ? ' ai-send-btn--ready' : ''}`}
          onClick={onSend}
          disabled={disabled || !hasContent}
          title="发送"
          aria-label="发送"
        >
          {loading ? (
            <span className="ai-send-spinner" />
          ) : (
            <IconSet name="send" size={15} />
          )}
        </button>
      </div>

      {/* 底部免责提示 */}
      <div className="ai-input-disclaimer">内容由 AI 生成，请仔细甄别</div>
    </div>
  );
};

export default InputBar;