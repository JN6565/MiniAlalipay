import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Toast } from 'antd-mobile';
import dayjs from 'dayjs';
import * as accountService from '@/services/account';
import { formatAmount, maskAccount } from '@/utils/format';
import { fetchAvailableFen } from '@/utils/balanceConfirm';
import { MonthGroupList, EmptyState, Skeleton, RevealToggle, IconSet } from '@/components/h5/common';
import type { IconName } from '@/components/h5/common/IconSet';
import { loadBankCardTransactions } from '@/pages/h5/Transactions';
import {
  isBalanceChangingTransaction,
  mergeUniqueTransactions,
} from '@/pages/h5/Transactions/viewModel';
import './index.less';

/** 每页条数：触底自动加载下一页。 */
const PAGE_SIZE = 20;

/**
 * 余额变动明细筛选胶囊（设计方案 2.2.2）：单行横向滑动，单选。
 * 收入/支出按方向；其余业务分类按账本 memo 关键词，与列表标题/图标规则一致。
 */
const ENTRY_CATEGORIES: { key: string; label: string; match: (tx: accountService.Transaction) => boolean }[] = [
  { key: 'all', label: '全部', match: () => true },
  { key: 'income', label: '收入', match: (tx) => tx.direction === 'IN' },
  { key: 'expense', label: '支出', match: (tx) => tx.direction === 'OUT' },
  { key: 'fund', label: '充值提现', match: (tx) => /充值|提现/.test(tx.memo || '') },
  { key: 'transfer', label: '转账', match: (tx) => (tx.memo || '').includes('转账') },
  { key: 'qrpay', label: '扫码', match: (tx) => /扫码|支付|收款/.test(tx.memo || '') },
  { key: 'repay', label: '花呗还款', match: (tx) => (tx.memo || '').includes('花呗还款') },
];

/** 列表图标：与账单页同源的业务归类。 */
const getEntryIcon = (memo: string | null): IconName => {
  const m = memo?.trim() || '';
  if (m.includes('充值')) return 'wallet';
  if (m.includes('提现')) return 'card';
  if (m.includes('转账')) return 'transfer';
  if (m.includes('花呗') || m.includes('还款')) return 'huabei';
  if (m.includes('支付') || m.includes('收款') || m.includes('扫码')) return 'qr';
  return 'receipt';
};

/** 列表主标题：转账区分收入/支出（设计方案 2.1.2 展示标题列）。 */
const getEntryTitle = (memo: string | null, direction: 'IN' | 'OUT'): string => {
  const m = memo?.trim() || '';
  if (m.includes('充值')) return '银行卡充值';
  if (m.includes('提现')) return '银行卡提现';
  if (m.includes('花呗还款') || m.includes('还款')) return '花呗还款';
  if (m.includes('转账')) {
    if (direction === 'IN') return m.includes('银行卡') ? '转账收入（银行卡）' : '转账收入';
    return m.includes('银行卡') ? '转账支出（银行卡）' : '转账支出';
  }
  if (m.includes('支付') || m.includes('收款') || m.includes('扫码')) {
    if (direction === 'IN') return m.includes('银行卡') ? '扫码收款（银行卡）' : '扫码收款';
    return '扫码支付';
  }
  return accountService.getLedgerEntryTitle({ memo });
};

/** 智能时间格式：当天 HH:mm，当年 MM-DD HH:mm，跨年 YYYY-MM-DD。 */
const formatEntryTime = (dateStr: string): string => {
  const t = dayjs(dateStr);
  if (!t.isValid()) return dateStr;
  const now = dayjs();
  if (t.isSame(now, 'day')) return t.format('HH:mm');
  if (t.isSame(now, 'year')) return t.format('MM-DD HH:mm');
  return t.format('YYYY-MM-DD');
};

/**
 * 余额变动明细页（/h5/wallet/balance-entries）：聚焦直接影响可用余额的流水。
 *
 * 数据策略（设计方案 2.3）：
 * 1. 主数据源为账本分录（GET /api/v1/accounts/me/entries，含 balance_after_fen）；
 * 2. 银行卡充值/提现不写分录，复用账单页 loadBankCardTransactions 投影合并；
 * 3. 前端排除花呗付款方的信用应收分录，保留收款方真实余额入账。
 *
 * 交互：固定时间倒序，筛选胶囊单选；每页 20 条，触底自动加载；按月份分组。
 */
const BalanceEntriesPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [entries, setEntries] = useState<accountService.Transaction[]>([]);
  const [category, setCategory] = useState('all');
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const [accountResp, data, bankCardItems] = await Promise.all([
          accountService.getMyAccount().catch(() => null),
          accountService.getTransactions({ pageSize: PAGE_SIZE }),
          loadBankCardTransactions(),
        ]);
        if (!cancelled) {
          if (accountResp) {
            setAccount(accountResp as unknown as accountService.AccountInfo);
          } else {
            // 账户接口失败时兜底单独补拉一次可用余额，保证顶部余额卡可用
            const balanceFen = await fetchAvailableFen();
            if (!cancelled && balanceFen !== null) {
              setAccount({ accountId: '', availableFen: balanceFen, frozenFen: 0, totalFen: balanceFen, status: 'ACTIVE' });
            }
          }
          // 银行卡直接出资不改变账户余额；花呗付款分录排除、收款入账保留。
          setEntries(mergeUniqueTransactions(
            (data.items || []).filter(isBalanceChangingTransaction),
            bankCardItems.filter(isBalanceChangingTransaction),
          ));
          setNextCursor(data.nextCursor);
        }
      } catch (error: any) {
        Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, []);

  /** 触底后按服务端游标继续加载账本，筛选只作用于已加载的余额变动事实。 */
  const loadMore = React.useCallback(async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const data = await accountService.getTransactions({ cursor: nextCursor, pageSize: PAGE_SIZE });
      setEntries((current) => mergeUniqueTransactions(
        current,
        (data.items || []).filter(isBalanceChangingTransaction),
      ));
      setNextCursor(data.nextCursor);
    } catch (error: any) {
      Toast.show({ content: error?.message || '加载更多余额明细失败，请稍后重试', icon: 'fail' });
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, nextCursor]);

  // 分类筛选：全部不过滤；收入/支出按方向；其余按 memo 关键词
  const filtered = useMemo(() => {
    const filter = ENTRY_CATEGORIES.find((c) => c.key === category) || ENTRY_CATEGORIES[0];
    return entries.filter(filter.match);
  }, [entries, category]);

  // 触底加载：服务端游标是唯一分页事实，切换筛选不改变当前滚动位置。
  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !nextCursor || loadingMore) return undefined;
    const observer = new IntersectionObserver(
      (observed) => {
        if (observed.some((e) => e.isIntersecting)) {
          void loadMore();
        }
      },
      { rootMargin: '120px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [filtered.length, loadMore, loadingMore, nextCursor]);

  return (
    <div className="balance-entries-page">
      {/* 顶部余额卡片：当前余额（掩码切换）+ 脱敏账户号；不展示总资产 */}
      <div className="be-hero">
        <div className="be-hero-label">当前余额</div>
        <div className="be-hero-balance">
          <RevealToggle
            defaultRevealed
            mask="****"
            value={`¥${formatAmount(account?.availableFen || 0)}`}
            valueClassName="be-hero-num"
          />
        </div>
        {account?.accountId ? <div className="be-hero-account">{maskAccount(account.accountId)}</div> : null}
      </div>

      <div className="be-body">
        {/* 筛选胶囊：横向滑动单选 */}
        <div className="be-chips-scroll">
          <div className="be-chips">
            {ENTRY_CATEGORIES.map((item) => (
              <span
                key={item.key}
                className={`be-chip${category === item.key ? ' active' : ''}`}
                onClick={() => setCategory(item.key)}
              >
                {item.label}
              </span>
            ))}
          </div>
        </div>

        {loading ? (
          <>
            <Skeleton variant="card" height={110} />
            <div style={{ marginTop: 10 }}>
              <Skeleton variant="card" height={110} />
            </div>
          </>
        ) : filtered.length === 0 ? (
          <>
            <EmptyState
              icon={<IconSet name="receipt" size={30} color="var(--h5-primary)" />}
              text={nextCursor ? '正在查找该分类明细' : '暂无余额变动记录'}
              hint={nextCursor ? '继续加载历史余额明细中…' : '充值、转账、收款完成后将在这里展示'}
            />
            {nextCursor && <div ref={sentinelRef} className="be-sentinel">加载中…</div>}
          </>
        ) : (
          <>
            <MonthGroupList
              items={filtered}
              getKey={(tx) => tx.transactionId}
              renderItem={(tx) => {
                const party = tx.counterpartyName
                  ? tx.direction === 'IN'
                    ? `来自 ${tx.counterpartyName}`
                    : `付给 ${tx.counterpartyName}`
                  : null;
                return (
                  <div className="be-row">
                    <div className="be-icon">
                      <IconSet name={getEntryIcon(tx.memo)} size={15} color="var(--h5-primary)" />
                    </div>
                    <div className="be-main">
                      <div className="be-title">{getEntryTitle(tx.memo, tx.direction)}</div>
                      {party && <div className="be-party">{party}</div>}
                      <div className="be-sub">
                        {formatEntryTime(tx.createdAt)}
                        {tx.balanceAfterFen !== null && (
                          <span> · 余额 ¥{formatAmount(tx.balanceAfterFen)}</span>
                        )}
                      </div>
                    </div>
                    <div className={`be-amount ${tx.direction === 'IN' ? 'amount-in' : ''}`}>
                      {tx.direction === 'IN' ? '+' : '−'}¥{formatAmount(tx.amountFen)}
                    </div>
                  </div>
                );
              }}
            />
            {nextCursor && (
              <div ref={sentinelRef} className="be-sentinel">
                {loadingMore ? '加载中…' : '继续上滑加载更多'}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default BalanceEntriesPage;
