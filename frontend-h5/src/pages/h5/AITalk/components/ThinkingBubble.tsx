import React, { useState, useCallback } from 'react';
import MarkdownContent from './MarkdownContent';

interface Props {
  /** 思考过程文本，可为空字符串 */
  thinking: string;
  /** 思考耗时（秒），用于折叠行展示 */
  seconds: number;
  /** 是否处于展开状态（受控或非受控均可） */
  defaultExpanded?: boolean;
}

/**
 * 思考过程折叠面板（DeepSeek 风格）。
 *
 * <p>默认折叠，标题为 "已思考（用时 X 秒）" + 右箭头图标。
 * 展开后渲染思考过程文本，支持 Markdown。</p>
 */
const ThinkingBubble: React.FC<Props> = ({ thinking, seconds, defaultExpanded = false }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  const toggle = useCallback(() => {
    setExpanded((v) => !v);
  }, []);

  // 无思考内容时返回 null，避免显示空折叠条
  if (!thinking) return null;

  const labelSeconds = seconds > 0 ? Math.round(seconds) : 1;

  return (
    <div className={`ai-thinking${expanded ? ' ai-thinking--expanded' : ''}`}>
      <button type="button" className="ai-thinking-header" onClick={toggle}>
        <span className="ai-thinking-icon">{expanded ? '▾' : '▸'}</span>
        <span className="ai-thinking-label">已思考（用时 {labelSeconds} 秒）</span>
      </button>
      {expanded && (
        <div className="ai-thinking-body">
          <MarkdownContent content={thinking} />
        </div>
      )}
    </div>
  );
};

export default ThinkingBubble;