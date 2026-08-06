import request from './request';

export interface CreditSummary {
  creditAccountId: string;
  totalLimitFen: number;
  usedFen: number;
  frozenFen: number;
  availableFen: number;
  unbilledFen: number;
  billedFen: number;
  overdueFen: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  suspendReason?: string;
}

export interface CreditBill {
  billId: string;
  period: string;
  status: 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE';
  totalFen: number;
  paidFen: number;
  outstandingFen: number;
  statementDate: string;
  dueAt: string;
}

export interface BillDetail extends CreditBill {
  items: Array<{
    purchaseId: string;
    amountFen: number;
    allocatedPaidFen: number;
    merchantName: string;
    occurredAt: string;
  }>;
  allocations: Array<{
    repaymentId: string;
    amountFen: number;
    createdAt: string;
  }>;
}

// 查询Mini花呗摘要
export const getCreditSummary = () => {
  return request.get<CreditSummary>('/api/v1/credit/me');
};

// 查询账单列表
export const getBills = (params?: { page?: number; pageSize?: number }) => {
  return request.get<{ items: CreditBill[]; total: number }>(
    '/api/v1/credit/bills',
    { params },
  );
};

// 查询账单详情
export const getBillDetail = (billId: string) => {
  return request.get<BillDetail>(`/api/v1/credit/bills/${billId}`);
};

// 创建还款草稿
export const createRepaymentDraft = (amountFen: number) => {
  return request.post<{
    repaymentDraftId: string;
    amountFen: number;
    allocation: Array<{
      targetType: string;
      targetId: string;
      amountFen: number;
    }>;
    expiresAt: string;
  }>('/api/v1/credit/repayment-drafts', { amountFen });
};

// 提交还款；契约字段为 repaymentDraftId + paymentProofToken（一次性支付密码证明）
export const submitRepayment = (params: {
  repaymentDraftId: string;
  paymentProofToken: string;
}) => {
  return request.post<{
    repaymentId: string;
    amountFen: number;
    status: string;
    createdAt: string;
    updatedAt: string;
  }>('/api/v1/credit/repayments', params);
};

// 查询还款状态
export const getRepaymentStatus = (repaymentId: string) => {
  return request.get<{
    repaymentId: string;
    transactionId: string;
    amountFen: number;
    status: string;
    restoredLimitFen: number;
  }>(`/api/v1/credit/repayments/${repaymentId}`);
};
