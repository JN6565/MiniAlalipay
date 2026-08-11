import React from 'react';
import { IconSet } from '@/components/h5/common';
import './index.less';

/** 关于页（V2）：品牌标识头 + 共用长文阅读模板。 */
const AboutPage: React.FC = () => {
  return (
    <div className="about-page">
      {/* 品牌标识头 */}
      <div className="about-logo">
        <IconSet name="wallet" size={24} color="#fff" />
      </div>
      <div className="about-name">MiniAI 支付</div>
      <div className="about-version">V1.0.0 · AI加持的确定性金融信任平台</div>

      <div className="about-card">
        <div className="about-section">
          <h2>用户协议</h2>
          <p>
            欢迎使用 MiniAI 支付。本系统为演示虚拟资金系统，不接入真实人民币通道。
            所有交易均为模拟交易，仅用于功能演示和学习目的。
          </p>
        </div>

        <div className="about-section">
          <h2>隐私政策</h2>
          <p>
            我们重视您的隐私保护。本系统收集的信息仅用于功能演示，不会用于商业用途。
            头像与昵称等资料仅保存在您的浏览器本地，所有数据将在演示结束后清除。
          </p>
        </div>
      </div>

      <div className="about-footer">© 2026 MiniAI 支付</div>
    </div>
  );
};

export default AboutPage;
