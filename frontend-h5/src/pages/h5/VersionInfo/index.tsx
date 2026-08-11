import React from 'react';
import { IconSet } from '@/components/h5/common';
import './index.less';

/** V1.0.0 更新说明（第二轮视觉升级内容）。 */
const UPDATE_ITEMS = [
  '全新视觉升级，卡片质感与图标体系焕新',
  '账单页新增收支分析视图',
  '扫一扫支持手动输入码内容降级',
  '招财喵淡粉轻语对话体验',
  '个人中心支持内置头像与资料编辑',
];

const VersionInfoPage: React.FC = () => {
  return (
    <div className="version-info-page">
      {/* 应用标识：渐变底 + 品牌图标 */}
      <div className="vi-logo">
        <IconSet name="wallet" size={26} color="#fff" />
      </div>
      <div className="vi-name">MiniAI 支付</div>
      <div className="vi-version">当前版本 V1.0.0</div>

      {/* 版本更新说明 */}
      <div className="vi-card">
        <div className="vi-card-title">版本更新说明</div>
        {UPDATE_ITEMS.map((text) => (
          <div key={text} className="vi-update-item">
            <span className="vi-update-dot" />
            <span className="vi-update-text">{text}</span>
          </div>
        ))}
      </div>

      {/* 技术栈说明 */}
      <div className="vi-card">
        <div className="vi-card-title">技术栈</div>
        <div className="vi-tech-row">
          <span className="vi-tech-label">前端</span>
          <span className="vi-tech-value">React + Umi + Ant Design Mobile</span>
        </div>
        <div className="vi-tech-row">
          <span className="vi-tech-label">后端</span>
          <span className="vi-tech-value">Spring Boot + Spring Cloud + Seata</span>
        </div>
        <div className="vi-tech-row last">
          <span className="vi-tech-label">AI 引擎</span>
          <span className="vi-tech-value">DeepSeek</span>
        </div>
      </div>

      <div className="vi-footer">© 2026 MiniAI 支付 · 演示环境，不接入真实资金通道</div>
    </div>
  );
};

export default VersionInfoPage;
