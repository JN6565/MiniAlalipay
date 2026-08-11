import React, { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as accountService from '@/services/account';
import { formatAmount, formatTime } from '@/utils/format';
import { MonthGroupList, EmptyState, Skeleton, IconSet } from '@/components/h5/common';
import type { IconName } from '@/components/h5/common/IconSet';
import AnalyticsPanel from './AnalyticsPanel';
import './index.less';

/** 业务类型：由账本摘要（memo）关键词推导，后端已脱敏的语义事实。 */
const BIZ_TYPES: { key: string; label: string; icon: IconName; match: (memo: string) => boolean }[] = [
  { key: 'recharge', label: '充值', icon: 'wallet', match: (m) => m.includes('充值') },
  { key: 'withdraw', label: '提现', icon: 'card', match: (m) => m.includes('提现') },
  { key: 'transfer', label: '转账', icon: 'transfer', match: (m) => m.includes('转账') },
  { key: 'credit', label: '花呗', icon: 'huabei', match: (m) => m.includes('花呗') || m.includes('还款') || m.includes('消费') },
  { key: 'pay', label: '支付', icon: 'qr', match: (m) => m.includes('支付') || m.includes('收款') },
];

const getBizType = (memo: string | null) => {
  const m = memo?.trim() || '';
  return BIZ_TYPES.find((t) => t.match(m)) || { key: 'other', label: '资金变动', icon: 'receipt' as IconName, match: () => true };
};

/**
 * 账单页（原明细页）：V2 双 Tab 结构——
 * 「账单」月分组列表 + 收支方向筛选；「分析」由原资产分析页迁移整合。
 */
const TransactionsPage: React.FC = () => {
  const [tab, setTab] = useState<'bills' | 'analytics'>('bills');
  const [loading, setLoading] = useState(true);
  const [transactions, setTransactions] = useState<accountService.Transaction[]>([]);
  const [direction, setDirection] = useState<'ALL' | 'IN' | 'OUT'>('ALL');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const params: any = { pageSize: 50 };
        if (direction !== 'ALL') {
          params.direction = direction;
        }
        const data = await accountService.getTransactions(params);
        if (!cancelled) {
          setTransactions(data.items || []);
        }
      } catch (error: any) {
        Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [direction]);

  const visible = useMemo(() => transactions, [transactions]);

  return (
    <div className="transactions-page">
      {/* 顶部双 Tab：账单 / 分析 */}
      <div className="tx-top-tabs">
        {([['bills', '账单'], ['analytics', '分析']] as const).map(([key, label]) => (
          <div
            key={key}
            className={`tx-top-tab${tab === key ? ' active' : ''}`}
            onClick={() => setTab(key)}
          >
            <span className="tx-top-tab-text">{label}</span>
            {tab === key && <span className="tx-top-tab-bar" />}
          </div>
        ))}
      </div>

      {tab === 'analytics' ? (
        <AnalyticsPanel />
      ) : (
        <div className="tx-bills-body">
          {/* 收支方向筛选 */}
          <div className="tx-chips">
            {([['ALL', '全部'], ['IN', '收入'], ['OUT', '支出']] as const).map(([key, label]) => (
              <span
                key={key}
                className={`tx-chip${direction === key ? ' active' : ''}`}
                onClick={() => setDirection(key)}
              >
                {label}
              </span>
            ))}
          </div>

          {loading ? (
            <>
              <Skeleton variant="card" height={110} />
              <div style={{ marginTop: 10 }}>
                <Skeleton variant="card" height={110} />
              </div>
            </>
          ) : visible.length === 0 ? (
            <EmptyState
              icon={<IconSet name="receipt" size={30} color="var(--h5-primary)" />}
              text="暂无交易记录"
              hint="充值、转账、花呗消费完成后将在这里展示"
            />
          ) : (
            <MonthGroupList
              items={visible}
              getKey={(tx) => String(tx.entryId)}
              renderItem={(tx) => {
                const biz = getBizType(tx.memo);
                const clickable = tx.memo?.includes('转账');
                const party = tx.counterpartyName
                  ? tx.direction === 'IN'
                    ? `来自 ${tx.counterpartyName}`
                    : `转给 ${tx.counterpartyName}`
                  : accountService.getLedgerEntryTitle(tx);
                return (
                  <div
                    className="tx-row"
                    onClick={() => clickable && history.push(`/h5/transfer/result/${tx.transactionId}`)}
                  >
                    <div className="tx-icon">
                      <IconSet name={biz.icon} size={15} color="var(--h5-primary)" />
                    </div>
                    <div className="tx-main">
                      <div className="tx-title">{biz.label} · {party}</div>
                      <div className="tx-sub">
                        {formatTime(tx.createdAt, 'YYYY-MM-DD HH:mm')}
                        {tx.balanceAfterFen !== null && (
                          <span className="tx-balance"> · 余额 ¥{formatAmount(tx.balanceAfterFen)}</span>
                        )}
                      </div>
                    </div>
                    <div className={`tx-amount ${tx.direction === 'IN' ? 'amount-in' : ''}`}>
                      {tx.direction === 'IN' ? '+' : '−'}¥{formatAmount(tx.amountFen)}
                    </div>
                  </div>
                );
              }}
            />
          )}
        </div>
      )}
    </div>
  );
};

export default TransactionsPage;
