import React from 'react';
import { history } from 'umi';
import './index.less';

const ContactsPage: React.FC = () => {
  return (
    <div className="contacts-page">
      <div className="contacts-content">
        <div className="empty-state">
          <div className="empty-icon">👥</div>
          <div className="empty-text">暂无联系人</div>
          <div className="empty-desc">添加常用联系人，转账更方便</div>
        </div>
      </div>

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab" onClick={() => history.push('/h5/home')}>
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/ai-talk')}>
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab on">
          <span className="tab-icon">👥</span>
          <span className="tab-label">联系人</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/profile')}>
          <span className="tab-icon">👤</span>
          <span className="tab-label">我的</span>
        </div>
      </div>
    </div>
  );
};

export default ContactsPage;
