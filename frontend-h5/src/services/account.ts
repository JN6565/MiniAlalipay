import request from './request';

export interface AccountInfo {
  accountId: string;
  availableFen: number;
  frozenFen: number;
  totalFen: number;
  status: string;
}

export interface Transaction {
  transactionId: string;
  businessType: string;
  amountFen: number;
  direction: 'IN' | 'OUT';
  counterparty: string;
  status: string;
  createdAt: string;
}

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
  return request.get<{ items: Transaction[]; total: number }>(
    '/api/v1/accounts/me/entries',
    { params: { limit: params.pageSize || 20 } },
  );
};

// 查询资产分析
export const getAnalytics = (range: '7d' | '30d' = '7d') => {
  return request.get<AnalyticsData>('/api/v1/accounts/me/analytics', {
    params: { range },
  });
};
