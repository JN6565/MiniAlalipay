import request from './request';

export interface AccountInfo {
  accountId: string;
  availableFen: number;
  frozenFen: number;
  totalFen: number;
  status: string;
}

export interface Transaction {
  entryId: number;
  transactionId: string;
  amountFen: number;
  direction: 'IN' | 'OUT';
  memo: string | null;
  counterpartyName: string;
  createdAt: string;
}

interface LedgerEntryResponse {
  entryId: number;
  transactionId: string;
  direction: 'DEBIT' | 'CREDIT';
  amountFen: number;
  memo: string | null;
  counterpartyName: string;
  createdAt: string;
}

interface LedgerEntryPageResponse {
  items: LedgerEntryResponse[];
  nextCursor: string | null;
}

/** 将账本会计方向转换为用户可理解的收支方向。 */
export const toCashFlowDirection = (direction: LedgerEntryResponse['direction']): Transaction['direction'] =>
  direction === 'CREDIT' ? 'IN' : 'OUT';

/** 使用后端已脱敏的账本摘要作为列表标题，避免凭空显示未知交易。 */
export const getLedgerEntryTitle = (entry: Pick<LedgerEntryResponse, 'memo'>): string =>
  entry.memo?.includes('充值')
    ? '账户充值'
    : entry.memo?.trim() || '账本明细';

export interface AnalyticsData {
  incomeFen: number;
  expenseFen: number;
  trend: Array<{
    date: string;
    incomeFen: number;
    expenseFen: number;
  }>;
  topPayees: Array<{
    userId: string;
    nickname: string;
    totalFen: number;
  }>;
}

// 查询本人账户
export const getMyAccount = () => {
  return request.get<AccountInfo>('/api/v1/accounts/me');
};

// 查询交易明细 - 使用账本明细接口
export const getTransactions = (params: {
  page?: number;
  pageSize?: number;
  direction?: 'IN' | 'OUT';
  status?: string;
}) => {
  return request.get<LedgerEntryPageResponse>(
    '/api/v1/accounts/me/entries',
    { params: { limit: params.pageSize || 20 } },
  ).then((page) => ({
    ...page,
    items: page.items
      .map((entry) => ({
        ...entry,
        direction: toCashFlowDirection(entry.direction),
      }))
      .filter((entry) => !params.direction || entry.direction === params.direction),
  }));
};

// 查询资产分析
export const getAnalytics = (range: '7d' | '30d' = '7d') => {
  return request.get<AnalyticsData>('/api/v1/accounts/me/analytics', {
    params: { range },
  });
};
