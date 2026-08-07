import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface Props {
  content: string;
}

/**
 * AI 回复 Markdown 渲染。
 * 使用 react-markdown（默认不解析 HTML，天然防 XSS），支持 GFM 表格/列表/删除线。
 */
const MarkdownContent: React.FC<Props> = ({ content }) => (
  <div className="ai-markdown">
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
  </div>
);

export default MarkdownContent;
