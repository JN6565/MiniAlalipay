import React, { useState, KeyboardEvent } from 'react';
import { SendOutline, FileOutline } from 'antd-mobile-icons';

/** 输入栏可启用的能力：与后端 agent 模式对应 */
export interface InputFeatures {
  /** 深度思考（让模型展示推理链；当前 UI 占位，未对应后端模式） */
  deepThinking?: boolean;
  /** 智能搜索（允许模型调用联网/工具；当前 UI 占位） */
  webSearch?: boolean;
}

interface Props {
  value: string;
  onChange: (v: string) => void;
  onSend: () => void;
  loading: boolean;
  disabled: boolean;
  /** 附件点击回调（当前未接入文件上传，UI 占位） */
  onAttach?: () => void;
  /** 功能切换回调 */
  onFeaturesChange?: (features: InputFeatures) => void;
  /** 受控的功能开关值 */
  features?: InputFeatures;
}

/**
 * 底部输入栏（参考 DeepSeek 风格）。
 *
 * <p>上半部分为功能切换 chips（深度思考 / 智能搜索），下半部分为输入框
 * + 附件按钮 + 发送按钮。支持回车发送、Shift+回车换行。</p>
 */
const InputBar: React.FC<Props> = ({
  value,
  onChange,
  onSend,
  loading,
  disabled,
  onAttach,
  onFeaturesChange,
  features,
}) => {
  // 非受控模式时本地维护
  const [localDeep, setLocalDeep] = useState(false);
  const [localSearch, setLocalSearch] = useState(false);

  const deepThinking = features?.deepThinking ?? localDeep;
  const webSearch = features?.webSearch ?? localSearch;

  /** 更新任一开关；受控模式交由父组件 */
  const update = (next: InputFeatures) => {
    if (onFeaturesChange) onFeaturesChange(next);
    else {
      if (next.deepThinking !== undefined) setLocalDeep(!!next.deepThinking);
      if (next.webSearch !== undefined) setLocalSearch(!!next.webSearch);
    }
  };

  const toggleDeep = () => update({ deepThinking: !deepThinking, webSearch });
  const toggleSearch = () => update({ deepThinking, webSearch: !webSearch });

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
      {/* 功能切换 chips */}
      <div className="ai-input-features">
        <button
          type="button"
          className={`ai-feature-chip${deepThinking ? ' ai-feature-chip--active' : ''}`}
          onClick={toggleDeep}
          disabled={loading}
        >
          <span className="ai-feature-icon">✦</span>
          <span>深度思考</span>
        </button>
        <button
          type="button"
          className={`ai-feature-chip${webSearch ? ' ai-feature-chip--active' : ''}`}
          onClick={toggleSearch}
          disabled={loading}
        >
          <span className="ai-feature-icon">⌕</span>
          <span>智能搜索</span>
        </button>
      </div>

      {/* 输入区：附件 + 输入框 + 发送按钮 */}
      <div className="ai-input-row">
        <button
          type="button"
          className="ai-attach-btn"
          onClick={onAttach}
          disabled={loading}
          title="添加附件"
          aria-label="添加附件"
        >
          <FileOutline />
        </button>

        <div className="ai-input-wrapper">
          <textarea
            className="ai-input"
            placeholder="发送消息给吱托芙…"
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