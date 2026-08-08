import React from 'react';
import './index.less';

const VersionInfoPage: React.FC = () => {
  return (
    <div className="version-info-page">
      <div className="version-header">
        <div className="app-icon">💰</div>
        <div className="app-name">MiniAlalipay</div>
        <div className="app-version">V1.0.0</div>
      </div>

      <div className="version-content">
        <div className="section">
          <div className="section-title">关于应用</div>
          <div className="section-text">
            MiniAlalipay 是一款 AI 驱动的金融信任平台，提供虚拟资金管理、转账、收款、信用服务等功能。
          </div>
        </div>

        <div className="section">
          <div className="section-title">技术栈</div>
          <div className="tech-list">
            <div className="tech-item">
              <span className="tech-label">前端</span>
              <span className="tech-value">React + Umi + Ant Design Mobile</span>
            </div>
            <div className="tech-item">
              <span className="tech-label">后端</span>
              <span className="tech-value">Spring Boot + Spring Cloud + Seata</span>
            </div>
            <div className="tech-item">
              <span className="tech-label">数据库</span>
              <span className="tech-value">MySQL 8.0 + Redis</span>
            </div>
            <div className="tech-item">
              <span className="tech-label">AI 引擎</span>
              <span className="tech-value">DeepSeek</span>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="section-title">更新日志</div>
          <div className="update-log">
            <div className="log-item">
              <div className="log-version">V1.0.0</div>
              <div className="log-date">2026-08-08</div>
              <div className="log-content">
                <p>• 首次发布</p>
                <p>• 支持转账、收款、充值功能</p>
                <p>• 集成 AI 智能助手</p>
                <p>• 支持 Mini 花呗信用服务</p>
                <p>• 好友系统与联系人管理</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VersionInfoPage;
