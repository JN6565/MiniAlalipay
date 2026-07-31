import React from 'react';
import { List } from 'antd-mobile';
import './index.less';

const AboutPage: React.FC = () => {
  return (
    <div className="about-page">
      <div className="about-header">
        <div className="about-logo">MiniAlalipay</div>
        <div className="about-version">V1.0.0</div>
        <div className="about-desc">AI加持的确定性金融信任平台</div>
      </div>

      <div className="about-content">
        <List header="用户协议">
          <List.Item>
            <div className="about-text">
              欢迎使用MiniAlalipay。本系统为演示虚拟资金系统，不接入真实人民币通道。所有交易均为模拟交易，仅用于功能演示和学习目的。
            </div>
          </List.Item>
        </List>

        <List header="隐私政策">
          <List.Item>
            <div className="about-text">
              我们重视您的隐私保护。本系统收集的信息仅用于功能演示，不会用于商业用途。所有数据将在演示结束后清除。
            </div>
          </List.Item>
        </List>
      </div>

      <div className="about-footer">
        <div className="about-copyright">© 2026 MiniAlalipay</div>
      </div>
    </div>
  );
};

export default AboutPage;
