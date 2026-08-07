import * as accountService from '../../src/services/account';
import request from '../../src/services/request';

jest.mock('../../src/services/request', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

const mockedGet = request.get as jest.Mock;

describe('账本明细展示映射', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  test('使用后端脱敏摘要作为明细标题', () => {
    const titleResolver = (accountService as any).getLedgerEntryTitle;
    const actual = typeof titleResolver === 'function'
      ? titleResolver({ memo: '普通转账付款' })
      : undefined;

    expect(actual).toBe('普通转账付款');
  });

  test('将充值账本摘要转换为用户可理解的标题', () => {
    const titleResolver = (accountService as any).getLedgerEntryTitle;
    const actual = typeof titleResolver === 'function'
      ? titleResolver({ memo: '充值贷记用户余额负债' })
      : undefined;

    expect(actual).toBe('账户充值');
  });

  test('将会计借贷方向转换为用户收支方向', () => {
    const directionResolver = (accountService as any).toCashFlowDirection;
    const debit = typeof directionResolver === 'function' ? directionResolver('DEBIT') : undefined;
    const credit = typeof directionResolver === 'function' ? directionResolver('CREDIT') : undefined;

    expect(debit).toBe('OUT');
    expect(credit).toBe('IN');
  });

  test('支出筛选只返回 DEBIT 分录', async () => {
    mockedGet.mockResolvedValue({
      items: [
        { entryId: 1, transactionId: 'out-1', direction: 'DEBIT', amountFen: 100, memo: '普通转账付款', createdAt: '2026-08-07T00:00:00Z' },
        { entryId: 2, transactionId: 'in-1', direction: 'CREDIT', amountFen: 200, memo: '充值贷记用户余额负债', createdAt: '2026-08-07T00:01:00Z' },
      ],
      nextCursor: null,
    });

    const result = await accountService.getTransactions({ direction: 'OUT' });

    expect(result.items.map((item) => item.transactionId)).toEqual(['out-1']);
  });

  test('收入筛选只返回 CREDIT 分录', async () => {
    mockedGet.mockResolvedValue({
      items: [
        { entryId: 1, transactionId: 'out-1', direction: 'DEBIT', amountFen: 100, memo: '普通转账付款', createdAt: '2026-08-07T00:00:00Z' },
        { entryId: 2, transactionId: 'in-1', direction: 'CREDIT', amountFen: 200, memo: '充值贷记用户余额负债', createdAt: '2026-08-07T00:01:00Z' },
      ],
      nextCursor: null,
    });

    const result = await accountService.getTransactions({ direction: 'IN' });

    expect(result.items.map((item) => item.transactionId)).toEqual(['in-1']);
  });
});
