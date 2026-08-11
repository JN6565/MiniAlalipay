import { describe, expect, test } from '@jest/globals';
import type { Transaction } from '@/services/account';
import {
  isBalanceChangingTransaction,
  mergeUniqueTransactions,
  projectBankCardTransaction,
} from './viewModel';

const ledgerTransaction = (overrides: Partial<Transaction> = {}): Transaction => ({
  entryId: 1,
  transactionId: 'txn-1',
  amountFen: 10000,
  direction: 'OUT',
  memo: '转账付款',
  counterpartyName: '小王',
  balanceAfterFen: 90000,
  createdAt: '2026-08-11T10:00:00Z',
  ...overrides,
});

describe('账单展示投影', () => {
  test('银行卡直接出资的转账和扫码支付不进入余额变动明细', () => {
    const transfer = projectBankCardTransaction({
      transactionId: 'bank-transfer',
      businessType: 'TRANSFER',
      amountFen: 10000,
      status: 'SUCCESS',
      createdAt: '2026-08-11T10:00:00Z',
    });
    const qrPay = projectBankCardTransaction({
      transactionId: 'bank-qr',
      businessType: 'QR_PAY',
      amountFen: 5000,
      status: 'SUCCESS',
      createdAt: '2026-08-11T09:00:00Z',
    });

    expect(isBalanceChangingTransaction(transfer)).toBe(false);
    expect(isBalanceChangingTransaction(qrPay)).toBe(false);
  });

  test('花呗付款分录排除但收款方余额入账保留', () => {
    const payer = ledgerTransaction({ memo: '信用支付确认：增加信用应收', direction: 'OUT', balanceAfterFen: null });
    const payee = ledgerTransaction({ memo: '信用支付确认：增加收款用户余额负债', direction: 'IN' });

    expect(isBalanceChangingTransaction(payer)).toBe(false);
    expect(isBalanceChangingTransaction(payee)).toBe(true);
  });

  test('同一交易优先保留带交易后余额的账本分录', () => {
    const creditEntry = ledgerTransaction({ entryId: 10, balanceAfterFen: null, memo: '花呗还款：减少信用应收', direction: 'IN' });
    const balanceEntry = ledgerTransaction({ entryId: 11, balanceAfterFen: 80000, memo: '花呗还款：扣减余额', direction: 'OUT' });

    expect(mergeUniqueTransactions([creditEntry, balanceEntry])).toEqual([balanceEntry]);
  });
});
