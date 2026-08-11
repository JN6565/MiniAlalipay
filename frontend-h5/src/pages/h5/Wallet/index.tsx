import { history, useLocation } from 'umi';
import { Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import * as accountService from '@/services/account';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
import { fetchAvailableFen } from '@/utils/balanceConfirm';
import { formatAmount, formatTime } from '@/utils/format';
import { Skeleton, RevealToggle, EmptyState, BankCardFace, IconSet } from '@/components/h5/common';
import { isBalanceChangingTransaction, loadBankCardTransactions, mergeUniqueTransactions } from '@/pages/h5/Transactions/viewModel';
import './index.less';

const RECENT_FILTERS = [
  { key: 'all', label: '全部', match: () => true },
  { key: 'income', label: '收入', match: (tx: accountService.Transaction) => tx.direction === 'IN' },
  { key: 'expense', label: '支出', match: (tx: accountService.Transaction) => tx.direction === 'OUT' },
] as const;

const getRecentTitle = (tx: accountService.Transaction): string => {
  const memo = tx.memo || '';
  if (memo.includes('充值')) return '银行卡充值';
  if (memo.includes('提现')) return '银行卡提现';
  if (memo.includes('花呗还款') || memo.includes('还款')) return '花呗还款';
  if (memo.includes('转账')) return tx.direction === 'IN' ? '转账收入' : '转账支出';
  if (memo.includes('收款')) return '扫码收款';
  if (memo.includes('支付') || memo.includes('扫码')) return '扫码支付';
  return accountService.getLedgerEntryTitle({ memo });
};

/**
 * 钱包页（原充值提现）：账户余额与银行卡的统一资金入口。
 *
 * V2 设计：顶部柔渐变区只展示账户余额（不展示总资产）；充值/提现胶囊按钮
 * 上移至悬浮操作卡，充值跳转 Popup 选卡流程；下方为银行卡卡面列表。
 *
 * 余额新鲜度保障（两层）：
 * 1. 每次进入页面重拉余额与卡列表；
 * 2. 充值/提现结果屏返回时携带 balanceDirty 标记：异步 TCC 入账可能滞后于页面跳转，
 *    标记存在时额外静默补拉最多 3 次（间隔 1s）并即时替换展示值，随后清除标记。
 */
const WalletPage = () => {
  const location = useLocation();
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [cards, setCards] = useState<BankCard[]>([]);
  const [recentEntries, setRecentEntries] = useState<accountService.Transaction[]>([]);
  const [recentFilter, setRecentFilter] = useState<typeof RECENT_FILTERS[number]['key']>('all');
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [accountResp, cardsResp, ledgerResp, bankCardResp] = await Promise.allSettled([
        accountService.getMyAccount(),
        getBankCards(),
        accountService.getTransactions({ pageSize: 20 }),
        loadBankCardTransactions(20),
      ]);
      if (accountResp.status === 'fulfilled') {
        // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
        setAccount(accountResp.value as unknown as accountService.AccountInfo);
      }
      if (cardsResp.status === 'fulfilled') {
        setCards(cardsResp.value || []);
      }
      const ledgerItems = ledgerResp.status === 'fulfilled' ? (ledgerResp.value.items || []) : [];
      const bankCardItems = bankCardResp.status === 'fulfilled' ? bankCardResp.value : [];
      setRecentEntries(mergeUniqueTransactions(
        ledgerItems.filter(isBalanceChangingTransaction),
        bankCardItems.filter(isBalanceChangingTransaction),
      ));
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '加载失败' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 充值/提现返回时的余额补拉：结果屏已轮询确认过一次，本页再静默补拉最多 3 次（间隔 1s）兜底滞后入账
  useEffect(() => {
    const navState = (location.state || {}) as { balanceDirty?: boolean };
    if (!navState.balanceDirty) return;
    // 先清除标记，避免 location 变更时重复触发补拉
    history.replace('/h5/wallet', {});
    let cancelled = false;
    const silentRefresh = async () => {
      for (let i = 0; i < 3; i++) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        if (cancelled) return;
        const balanceFen = await fetchAvailableFen();
        if (cancelled) return;
        if (balanceFen !== null) {
          // 静默替换展示值：不进骨架屏，不打断用户浏览
          setAccount((prev) => (prev ? { ...prev, availableFen: balanceFen } : prev));
        }
      }
    };
    silentRefresh();
    return () => { cancelled = true; };
  }, [location.state]);

  if (loading) {
    return (
      <div className="wallet-page">
        <div className="wallet-hero">
          <Skeleton variant="card" height={72} />
        </div>
        <div style={{ marginTop: 14 }}>
          <Skeleton variant="card" height={120} />
        </div>
        <div style={{ marginTop: 14 }}>
          <Skeleton variant="card" height={120} />
        </div>
      </div>
    );
  }

  const filter = RECENT_FILTERS.find((item) => item.key === recentFilter) || RECENT_FILTERS[0];
  const latestEntries = recentEntries.filter(filter.match).slice(0, 5);

  return (
    <div className="wallet-page">
      {/* 顶部品牌区：柔渐变 + 账户余额（掩码切换，不展示总资产） */}
      <div className="wallet-hero">
        <div className="hero-label">账户余额（元）</div>
        <div className="hero-balance">
          <RevealToggle
            defaultRevealed
            mask="****"
            value={formatBalance(account?.availableFen || 0)}
            valueClassName="hero-num"
          />
        </div>
      </div>

      {/* 悬浮操作卡：充值（渐变实心）/ 提现（蓝描边），位于银行卡列表上方 */}
      <div className="wallet-action-card">
        <div className="wallet-action-btn primary" onClick={() => history.push('/h5/recharge')}>
          充值
        </div>
        <div className="wallet-action-btn outline" onClick={() => history.push('/h5/withdraw')}>
          提现
        </div>
      </div>

      {/* 银行卡列表 */}
      <div className="wallet-section-head">
        <span className="section-title">银行卡</span>
        <span className="section-link" onClick={() => history.push('/h5/bank-cards')}>
          管理银行卡 <IconSet name="chevronRight" size={12} />
        </span>
      </div>

      {cards.length === 0 ? (
        <EmptyState
          icon={<IconSet name="card" size={30} color="var(--h5-primary)" />}
          text="还没有绑定银行卡"
          hint="绑定后可从银行卡充值到账户余额"
          actionText="去绑卡"
          onAction={() => history.push('/h5/bank-cards')}
        />
      ) : (
        <div className="wallet-card-list">
          {cards.map((card) => (
            <BankCardFace
              key={card.cardId}
              bankCode={card.bankCode}
              bankName={card.bankName}
              cardType={card.cardType}
              cardLast4={card.cardLast4}
              isDefault={card.isDefault}
              balanceFen={card.balanceFen}
              balanceRevealed={false}
              onClick={() => history.push(`/h5/bank-cards/${card.cardId}`)}
            />
          ))}
        </div>
      )}

      {/* 最近 5 条余额明细：仅展示直接影响可用余额的流水，完整列表进入余额明细页。 */}
      <div className="wallet-recent-panel">
        <div className="wallet-section-head inline">
          <span className="section-title">余额明细</span>
          <span className="section-link" onClick={() => history.push('/h5/wallet/balance-entries')}>
            查看全部 <IconSet name="chevronRight" size={12} />
          </span>
        </div>
        <div className="wallet-entry-chips">
          {RECENT_FILTERS.map((item) => (
            <span
              key={item.key}
              className={`wallet-entry-chip${recentFilter === item.key ? ' active' : ''}`}
              onClick={() => setRecentFilter(item.key)}
            >
              {item.label}
            </span>
          ))}
        </div>
        {latestEntries.length === 0 ? (
          <div className="wallet-entry-empty">暂无余额变动记录</div>
        ) : latestEntries.map((tx) => (
          <div className="wallet-entry-row" key={tx.transactionId} onClick={() => history.push('/h5/wallet/balance-entries')}>
            <div className="wallet-entry-icon">
              <IconSet name="receipt" size={14} color="var(--h5-primary)" />
            </div>
            <div className="wallet-entry-main">
              <div className="wallet-entry-title">
                {getRecentTitle(tx)}
                {tx.counterpartyName ? ` · ${tx.direction === 'IN' ? '来自' : '付给'} ${tx.counterpartyName}` : ''}
              </div>
              <div className="wallet-entry-sub">
                {formatTime(tx.createdAt, 'MM-DD HH:mm')}
                {tx.balanceAfterFen !== null && ` · 余额 ¥${formatAmount(tx.balanceAfterFen)}`}
              </div>
            </div>
            <div className={`wallet-entry-amount${tx.direction === 'IN' ? ' amount-in' : ''}`}>
              {tx.direction === 'IN' ? '+' : '−'}¥{formatAmount(tx.amountFen)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default WalletPage;
