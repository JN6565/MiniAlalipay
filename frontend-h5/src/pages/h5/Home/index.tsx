import React, { useState, useEffect } from 'react';
import { history } from 'umi';
import { Toast, SpinLoading } from 'antd-mobile';
import { ScanCodeOutline } from 'antd-mobile-icons';
import * as accountService from '@/services/account';
import * as creditService from '@/services/credit';
import { useTabActiveRefresh } from '@/utils/useTabActiveRefresh';
import './index.less';

const HomePage: React.FC = () => {
  const nickname = localStorage.getItem('nickname') || '用户';
  const [loading, setLoading] = useState(true);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [transactions, setTransactions] = useState<accountService.Transaction[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  // 首页保活常驻，转账/充值等业务完成后回切时静默重拉，
  // 余额与最近交易始终以服务端最新事实为准。
  useTabActiveRefresh('/h5/home', () => loadData(true));

  const loadData = async (silent = false) => {
    // 静默刷新不进入全屏加载态：保留旧内容直至新数据到达，避免回切时闪加载圈。
    if (!silent) {
      setLoading(true);
    }
    try {
      const [accountResult, creditResult, txResult] = await Promise.allSettled([
        accountService.getMyAccount(),
        creditService.getCreditSummary(),
        accountService.getTransactions({ pageSize: 5 }),
      ]);

      if (accountResult.status === 'fulfilled') {
        setAccount(accountResult.value);
      }

      if (creditResult.status === 'fulfilled') {
        setCredit(creditResult.value);
      }
      // 信用 API 失败时保持 credit 为 null，UI 层显示"暂无数据"

      if (txResult.status === 'fulfilled') {
        setTransactions(txResult.value.items || []);
      }
    } catch (error) {
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatAmount = (fen: number) => {
    return (fen / 100).toFixed(2);
  };

  const formatRelativeTime = (dateStr: string) => {
    const now = new Date();
    const date = new Date(dateStr);
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    if (diffMins < 1) return '刚刚';
    if (diffMins < 60) return `${diffMins}分钟前`;
    if (diffHours < 24) return `${diffHours}小时前`;
    if (diffDays < 7) return `${diffDays}天前`;
    return date.toLocaleDateString('zh-CN');
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

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
            <span onClick={() => history.push('/h5/scan')}><ScanCodeOutline /></span>
          </div>
        </div>

        <div className="asset-box">
          <div className="asset-top">
            <span>总资产(元)</span>
            <span className="eye">👁️</span>
          </div>
          <div className="asset-num">{formatAmount(account?.totalFen || 0)}</div>
          <div className="asset-cols">
            <div>
              <span>可用余额</span>
              <b>{formatAmount(account?.availableFen || 0)}</b>
            </div>
            <div>
              <span>冻结金额</span>
              <b>{formatAmount(account?.frozenFen || 0)}</b>
            </div>
            <div>
              <span>花呗可用</span>
              <b>{credit ? formatAmount(credit.availableFen) : '--'}</b>
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
        <div className="func-item" onClick={() => history.push('/h5/recharge')}>
          <div className="func-icon" style={{ background: '#f5f5f5' }}>🏦</div>
          <span>充值</span>
        </div>
        <div className="func-item" onClick={() => history.push('/h5/bank-cards')}>
          <div className="func-icon" style={{ background: '#f5f5f5' }}>💳</div>
          <span>银行卡</span>
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
                <div className="tx-name">{accountService.getLedgerEntryTitle(tx)}</div>
                <div className="tx-time">{formatRelativeTime(tx.createdAt)}</div>
              </div>
              <div className={`tx-amt ${tx.direction === 'IN' ? 'in' : 'out'}`}>
                {tx.direction === 'IN' ? '+' : '-'}{formatAmount(tx.amountFen)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default HomePage;
