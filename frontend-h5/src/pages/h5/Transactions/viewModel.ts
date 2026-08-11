import type { Transaction } from '@/services/account';
import type { BankCardTransaction } from '@/services/bankCard';

/**
 * 将银行卡流水投影为用户全局账单条目。
 *
 * 银行卡出资交易不写账户中心账本，因此付款方账单必须使用卡流水补齐；
 * 投影条目的 entryId 固定为 0，调用方以 transactionId 作为跨数据源去重依据。
 */
export const projectBankCardTransaction = (tx: BankCardTransaction): Transaction => {
  const isIn = tx.businessType === 'BANK_CARD_RECHARGE';
  const memo = tx.businessType === 'BANK_CARD_RECHARGE'
    ? '银行卡充值'
    : tx.businessType === 'TRANSFER'
      ? '银行卡转账'
      : tx.businessType === 'QR_PAY'
        ? '银行卡扫码支付'
        : '银行卡提现';

  return {
    entryId: 0,
    transactionId: tx.transactionId,
    amountFen: tx.amountFen,
    direction: isIn ? 'IN' : 'OUT',
    memo,
    counterpartyName: '',
    balanceAfterFen: null,
    createdAt: tx.createdAt,
  };
};

/**
 * 判断账单条目是否直接改变本人账户可用余额。
 *
 * 银行卡直接出资的转账/扫码支付只扣银行卡虚拟余额，不得进入账户余额明细；
 * 花呗付款方的信用应收分录不改变余额，但收款方的入账分录必须保留。
 */
export const isBalanceChangingTransaction = (tx: Transaction): boolean => {
  const memo = tx.memo || '';
  if (tx.entryId === 0 && /银行卡转账|银行卡扫码支付/.test(memo)) return false;
  if (tx.direction === 'OUT' && /信用支付|花呗消费/.test(memo)) return false;
  return true;
};

/**
 * 合并账本分录与银行卡流水，并保证同一交易在用户账单中只展示一次。
 *
 * 同一交易存在多个本人科目分录时，优先选择带交易后余额的用户余额分录；
 * 其次选择真实账本分录，避免兼容期内账本和银行卡投影同时存在时重复展示。
 */
export const mergeUniqueTransactions = (...groups: Transaction[][]): Transaction[] => {
  const selected = new Map<string, Transaction>();

  for (const tx of groups.flat()) {
    const current = selected.get(tx.transactionId);
    if (!current) {
      selected.set(tx.transactionId, tx);
      continue;
    }

    const currentHasBalance = current.balanceAfterFen !== null;
    const nextHasBalance = tx.balanceAfterFen !== null;
    if ((!currentHasBalance && nextHasBalance)
      || (currentHasBalance === nextHasBalance && current.entryId === 0 && tx.entryId !== 0)) {
      selected.set(tx.transactionId, tx);
    }
  }

  return [...selected.values()].sort((a, b) =>
    (b.createdAt || '').localeCompare(a.createdAt || ''),
  );
};
