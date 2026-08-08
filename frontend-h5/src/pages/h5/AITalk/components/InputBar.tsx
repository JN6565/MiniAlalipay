import React, { KeyboardEvent } from 'react';
import { SendOutline } from 'antd-mobile-icons';

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

  const hasContent = !!value.trim();

  return (
    <div className="ai-input-bar">
      {/* 输入区：附件 + 输入框 + 发送按钮 */}
      <div className="ai-input-row">
        <div className="ai-input-wrapper">
          <textarea
            className="ai-input"
            placeholder="发送消息给财喵…"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
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
            <SendOutline fontSize={18} />
          )}
        </button>
      </div>

      {/* 底部免责提示 */}
      <div className="ai-input-disclaimer">内容由 AI 生成，请仔细甄别</div>
    </div>
  );
};

export default InputBar;