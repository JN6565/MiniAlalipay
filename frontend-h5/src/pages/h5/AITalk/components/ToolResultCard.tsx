import React from 'react';
import type { ToolResultMessage } from '../types';

interface Props {
  message: ToolResultMessage;
}

/**
 * 格式化分→元，保留两位小数。
 */
function formatFen(fen: unknown): string {
  const n = Number(fen);
  if (Number.isNaN(n)) return '--';
  return (n / 100).toFixed(2);
}

/**
 * 余额查询卡片。
 */
const BalanceCard: React.FC<{ data: Record<string, any> }> = ({ data }) => {
  const availableFen = data.availableFen ?? data.balanceFen;
  const frozenFen = data.frozenFen;
  return (
    <div className="ai-card ai-card-balance">
      <div className="ai-card-icon">💰</div>
      <div className="ai-card-label">可用余额</div>
      <div className="ai-card-amount">¥{formatFen(availableFen)}</div>
      {frozenFen != null && (
        <div className="ai-card-sub">冻结 ¥{formatFen(frozenFen)}</div>
      )}
    </div>
  );
};

/**
 * 花呗摘要卡片（总额度 + 已用 + 可用 + 进度条）。
 */
const CreditSummaryCard: React.FC<{ data: Record<string, any> }> = ({ data }) => {
  const totalLimitFen = data.totalLimitFen;
  const usedFen = data.usedFen ?? data.usedAmountFen;
  const availableFen = data.availableFen;
  const pct = totalLimitFen > 0 ? Math.round((Number(usedFen) / Number(totalLimitFen)) * 100) : 0;
  return (
    <div className="ai-card ai-card-credit">
      <div className="ai-card-icon">🌸</div>
      <div className="ai-card-label">Mini 花呗</div>
      <div className="ai-card-row">
        <span>已用</span>
        <span className="ai-card-bold">¥{formatFen(usedFen)}</span>
        <span>/ 总额度 ¥{formatFen(totalLimitFen)}</span>
      </div>
      <div className="ai-card-bar">
        <div className="ai-card-bar-fill" style={{ width: `${Math.min(pct, 100)}%` }} />
      </div>
      {availableFen != null && (
        <div className="ai-card-sub">剩余可用 ¥{formatFen(availableFen)}</div>
      )}
    </div>
  );
};

/**
 * 交易明细卡片（列表）。
 */
const TransactionsCard: React.FC<{ data: Record<string, any> }> = ({ data }) => {
  const list = (data.transactions ?? data.items ?? data.list ?? []) as any[];
  if (!list || list.length === 0) return <div className="ai-card-empty">暂无交易记录</div>;
  return (
    <div className="ai-card ai-card-list">
      <div className="ai-card-label">最近交易</div>
      {list.slice(0, 5).map((txn: any, i: number) => (
        <div key={txn.id ?? i} className="ai-card-list-row">
          <div className="ai-card-list-left">
            <span className="ai-card-list-title">{txn.title ?? txn.remark ?? txn.type ?? '交易'}</span>
            <span className="ai-card-list-date">{txn.time ?? txn.createdAt ?? ''}</span>
          </div>
          <span className={Number(txn.amountFen) >= 0 ? 'ai-card-amount-in' : 'ai-card-amount-out'}>
            {Number(txn.amountFen) >= 0 ? '+' : ''}¥{formatFen(Math.abs(Number(txn.amountFen ?? 0)))}
          </span>
        </div>
      ))}
    </div>
  );
};

/**
 * 转账结果卡片。
 */
const TransferCard: React.FC<{ data: Record<string, any> }> = ({ data }) => (
  <div className="ai-card ai-card-transfer">
    <div className="ai-card-icon">💸</div>
    <div className="ai-card-label">转账</div>
    {data.payeeNickname && <div className="ai-card-row">收款人：{data.payeeNickname}</div>}
    {data.amountFen != null && (
      <div className="ai-card-amount">¥{formatFen(data.amountFen)}</div>
    )}
    {data.remark && <div className="ai-card-sub">备注：{data.remark}</div>}
  </div>
);

/**
 * 花呗账单列表卡片。
 */
const CreditBillsCard: React.FC<{ data: Record<string, any> }> = ({ data }) => {
  const list = (data.bills ?? data.items ?? data.list ?? []) as any[];
  if (!list || list.length === 0) return <div className="ai-card-empty">暂无账单</div>;
  return (
    <div className="ai-card ai-card-list">
      <div className="ai-card-label">花呗账单</div>
      {list.slice(0, 5).map((bill: any, i: number) => (
        <div key={bill.id ?? i} className="ai-card-list-row">
          <div className="ai-card-list-left">
            <span className="ai-card-list-title">{bill.period ?? bill.month ?? `账单 ${i + 1}`}</span>
            <span className="ai-card-list-date">{bill.status ?? ''}</span>
          </div>
          <span className="ai-card-amount-out">¥{formatFen(bill.amountFen ?? bill.totalAmountFen)}</span>
        </div>
      ))}
    </div>
  );
};

/**
 * 交易状态查询卡片。
 */
const TransactionStatusCard: React.FC<{ data: Record<string, any> }> = ({ data }) => (
  <div className="ai-card ai-card-status">
    <div className="ai-card-icon">📋</div>
    <div className="ai-card-label">交易状态</div>
    <div className="ai-card-row">
      <span>状态：</span>
      <span className="ai-card-bold">{data.status ?? '未知'}</span>
    </div>
    {data.amountFen != null && (
      <div className="ai-card-row">金额：¥{formatFen(data.amountFen)}</div>
    )}
  </div>
);

/**
 * 失败卡片（统一展示错误信息）。
 */
const FailedCard: React.FC<{ summary: string }> = ({ summary }) => {
  const safeSummary =
    summary && summary.trim() && summary.trim().toLowerCase() !== 'null'
      ? summary.trim()
      : '操作未能完成，请稍后重试或改用传统操作表单。';
  return (
    <div className="ai-card ai-card-failed">
      <div className="ai-card-icon">⚠️</div>
      <div className="ai-card-label">操作未成功</div>
      <div className="ai-card-sub">{safeSummary}</div>
    </div>
  );
};

/**
 * 加载中卡片（tool-call 事件已发出，等待结果）。
 */
const LoadingCard: React.FC<{ tool: string }> = ({ tool }) => {
  const labels: Record<string, string> = {
    get_balance: '正在查询余额…',
    get_credit_summary: '正在查询花呗额度…',
    list_transactions: '正在查询交易记录…',
    list_credit_bills: '正在查询花呗账单…',
    get_transaction_status: '正在查询交易状态…',
    search_payees: '正在搜索收款人…',
    get_account_summary: '正在查询账户摘要…',
    create_transfer_draft: '正在创建转账草稿…',
    create_credit_repayment_draft: '正在创建还款草稿…',
  };
  return (
    <div className="ai-card ai-card-loading">
      <div className="ai-card-icon">⏳</div>
      <span>{labels[tool] ?? '正在执行操作…'}</span>
      <div className="ai-card-spinner" />
    </div>
  );
};

/**
 * 工具结果卡片分发组件。
 * 根据 tool 名称和状态渲染对应的卡片样式。
 */
const ToolResultCard: React.FC<Props> = ({ message }) => {
  // 加载态
  if (message.loading) {
    return <LoadingCard tool={message.tool} />;
  }

  // 失败态
  if (message.status === 'failed') {
    return <FailedCard summary={message.summary} />;
  }

  const { data, tool } = message;

  // 按工具名分发卡片
  switch (tool) {
    case 'get_balance':
    case 'get_account_summary':
      return <BalanceCard data={data} />;
    case 'get_credit_summary':
      return <CreditSummaryCard data={data} />;
    case 'list_transactions':
      return <TransactionsCard data={data} />;
    case 'list_credit_bills':
      return <CreditBillsCard data={data} />;
    case 'get_transaction_status':
      return <TransactionStatusCard data={data} />;
    case 'create_transfer_draft':
    case 'validate_transfer_draft':
    case 'submit_confirmed_transfer':
      return <TransferCard data={data} />;
    case 'search_payees': {
      const users = (data.users ?? []) as any[];
      return (
        <div className="ai-card ai-card-list">
          <div className="ai-card-label">候选收款人</div>
          {users.length === 0 ? (
            <div className="ai-card-empty">未找到匹配用户</div>
          ) : (
            users.slice(0, 5).map((u: any, i: number) => (
              <div key={u.userId ?? i} className="ai-card-list-row">
                <span className="ai-card-list-title">{u.nickname ?? u.name ?? u.label}</span>
                <span className="ai-card-list-date">{u.phoneTail ? `尾号${u.phoneTail}` : (u.phone ?? u.mobile ?? u.accountNumber ?? '')}</span>
              </div>
            ))
          )}
        </div>
      );
    }
    default:
      // 通用卡片：展示摘要
      return (
        <div className="ai-card ai-card-generic">
          <div className="ai-card-icon">✅</div>
          <div className="ai-card-label">操作完成</div>
          <div className="ai-card-sub">{message.summary}</div>
        </div>
      );
  }
};

export default ToolResultCard;
