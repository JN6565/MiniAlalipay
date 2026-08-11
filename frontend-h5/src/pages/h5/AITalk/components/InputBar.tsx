import React, { KeyboardEvent, useEffect } from 'react';
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
   * 软键盘检测：以 visualViewport 实测高度变化判断键盘弹出/收起，
   * 而非 focus/blur——桌面端仅点击输入框不会误隐藏 TabBar 产生跳动。
   * 高度差超过 120px 才认定键盘（排除浏览器地址栏收展引起的几十像素波动）。
   * 弹出时给 body 挂 ai-keyboard-open（TabBar 隐藏），并写入实测键盘高度
   * 到 --ai-keyboard-offset：Android 布局视口随键盘收缩、偏移为 0；
   * iOS 布局视口不收缩、偏移即键盘高度，由样式托起输入栏贴合键盘上沿。
   */
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return undefined;
    const baseHeight = vv.height;

    const handleViewportResize = () => {
      const keyboardHeight = Math.max(0, baseHeight - vv.height);
      const isOpen = keyboardHeight > 120;
      document.body.classList.toggle('ai-keyboard-open', isOpen);
      // window.innerHeight 未同步收缩 → iOS 行为，需手动抬高输入栏
      const layoutShrunk = window.innerHeight < baseHeight - 120;
      const offset = isOpen && !layoutShrunk ? keyboardHeight : 0;
      document.body.style.setProperty('--ai-keyboard-offset', `${offset}px`);
    };

    vv.addEventListener('resize', handleViewportResize);
    return () => {
      vv.removeEventListener('resize', handleViewportResize);
      // 组件卸载时清理状态，避免残留类名影响其他页面的 TabBar
      document.body.classList.remove('ai-keyboard-open');
      document.body.style.removeProperty('--ai-keyboard-offset');
    };
  }, []);

  /**
   * 聚焦后延迟把输入框滚入可见区域，确保最后一条消息不被输入栏遮挡
   * （键盘弹出后的视口收缩由上方 visualViewport 逻辑兼容）。
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
            placeholder="和招财喵说点什么…"
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