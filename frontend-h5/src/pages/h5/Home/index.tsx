import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { formatRelativeTime } from '@/utils/format';
import './index.less';

const HomePage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const nickname = localStorage.getItem('nickname') || '用户';

  // TODO: 对接真实接口
  useEffect(() => {
    Toast.show({ content: '后端服务未启动', icon: 'fail' });
    setLoading(false);
  }, []);

  // Mock数据
  const account = {
    totalFen: 0,
    availableFen: 0,
    frozenFen: 0,
  };

  const credit = {
    availableFen: 0,
    usedFen: 0,
  };

  const transactions: any[] = [];

  const formatAmount = (fen: number) => {
    return (fen / 100).toFixed(2);
  };

  return (
    <div className="home">
      {/* 顶部资产区域 */}
      <div className="header">
        <div className="header-row">
          <div className="hi">
            <div className="avatar">👤</div>
            <span>{nickname}</span>
          </div>
          <div className="header-btns">
            <span onClick={() => history.push('/h5/settings')}>⚙️</span>
            <span>🔔</span>
          </div>
        </div>

        <div className="asset-box">
          <div className="asset-top">
            <span>总资产(元)</span>
            <span className="eye">👁️</span>
          </div>
          <div className="asset-num">{formatAmount(account.totalFen)}</div>
          <div className="asset-cols">
            <div>
              <span>可用余额</span>
              <b>{formatAmount(account.availableFen)}</b>
            </div>
            <div>
              <span>冻结金额</span>
              <b>{formatAmount(account.frozenFen)}</b>
            </div>
            <div>
              <span>花呗可用</span>
              <b>{formatAmount(credit.availableFen)}</b>
            </div>
          </div>
        </div>
      </div>

      {/* 功能入口 */}
      <div className="funcs">
        <div className="func-item" onClick={() => history.push('/h5/transfer')}>
          <div className="func-icon" style={{ background: '#e6f7ff' }}>💸</div>
          <span>转账</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/ai-talk')}>
          <div className="func-icon" style={{ background: '#f6ffed' }}>🤖</div>
          <span>AI助手</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/collection')}>
          <div className="func-icon" style={{ background: '#fff7e6' }}>📱</div>
          <span>收款</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/credit')}>
          <div className="func-icon" style={{ background: '#fff2f0' }}>💳</div>
          <span>花呗</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/account/transactions')}>
          <div className="func-icon" style={{ background: '#f9f0ff' }}>📊</div>
          <span>明细</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/account/analytics')}>
          <div className="func-icon" style={{ background: '#e6fffb' }}>📈</div>
          <span>分析</span>
        </div>
        <div className="func-item">
          <div className="func-icon" style={{ background: '#f5f5f5' }}>🏦</div>
          <span>充值</span>
        </div>
        <div className="func-item">
          <div className="func-icon" style={{ background: '#f5f5f5' }}>❓</div>
          <span>帮助</span>
        </div>
      </div>

      {/* 最近交易 */}
      <div className="tx-box">
        <div className="tx-head">
          <span>最近交易</span>
          <span className="more" onClick={() => history.push('/h5/account/transactions')}>查看全部 &gt;</span>
        </div>

        {transactions.length === 0 ? (
          <div style={{ padding: '40px 0', textAlign: 'center', color: '#999', fontSize: 14 }}>
            暂无交易记录
          </div>
        ) : (
          transactions.map((tx) => (
            <div key={tx.transactionId} className="tx-item">
              <div className="tx-icon">
                {tx.direction === 'IN' ? '📥' : '📤'}
              </div>
              <div className="tx-mid">
                <div className="tx-name">{tx.counterparty || '未知'}</div>
                <div className="tx-time">{formatRelativeTime(tx.createdAt)}</div>
              </div>
              <div className={`tx-amt ${tx.direction === 'IN' ? 'in' : 'out'}`}>
                {tx.direction === 'IN' ? '+' : '-'}{formatAmount(tx.amountFen)}
              </div>
            </div>
          ))
        )}
      </div>

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab on">
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/ai-talk')}>
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/collection')}>
          <span className="tab-icon">📱</span>
          <span className="tab-label">收款</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/credit')}>
          <span className="tab-icon">💳</span>
          <span className="tab-label">花呗</span>
        </div>
      </div>
    </div>
  );
};

export default HomePage;
