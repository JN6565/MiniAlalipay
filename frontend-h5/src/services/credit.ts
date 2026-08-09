import request from './request';
import { generateIdempotencyKey } from './utils';

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

/**
 * 信用消费明细。对应 OpenAPI CreditPurchase schema。
 * billingStatus 出账状态：UNBILLED 未出账 / BILLED 已出账 / REPAID 已还清 / REVERSED 已冲正。
 */
export interface CreditPurchase {
  purchaseId: string;
  creditTransactionId: string;
  qrOrderId: string;
  amountFen: number;
  repaidFen: number;
  outstandingFen: number;
  billingStatus: 'UNBILLED' | 'BILLED' | 'REPAID' | 'REVERSED';
  occurredAt: string;
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
export const getBills = (): Promise<CreditBill[]> => {
  return request.get<CreditBill[]>('/api/v1/credit/bills') as unknown as Promise<CreditBill[]>;
};

// 查询信用消费明细；billingStatus 可选，按出账状态筛选（UNBILLED/BILLED/REPAID/REVERSED），不传时不发送查询参数
export const getPurchases = (billingStatus?: string): Promise<CreditPurchase[]> => {
  const path = billingStatus
    ? `/api/v1/credit/purchases?billingStatus=${billingStatus}`
    : '/api/v1/credit/purchases';
  return request.get<CreditPurchase[]>(path) as unknown as Promise<CreditPurchase[]>;
};

// 查询账单详情
export const getBillDetail = (billId: string) => {
  return request.get<BillDetail>(`/api/v1/credit/bills/${billId}`);
};

/**
 * 创建还款草稿；契约要求携带 Idempotency-Key，超时可传入同一幂等键重试
 * @param amountFen 还款金额（分）
 * @param idempotencyKey 幂等键，缺省时自动生成；重试同一笔还款时必须复用原键
 */
export const createRepaymentDraft = (amountFen: number, idempotencyKey?: string) => {
  return request.post<{
    repaymentDraftId: string;
    amountFen: number;
    allocation: Array<{
      targetType: string;
      targetId: string;
      amountFen: number;
    }>;
    expiresAt: string;
  }>('/api/v1/credit/repayment-drafts', { amountFen }, {
    headers: { 'Idempotency-Key': idempotencyKey || generateIdempotencyKey() },
  });
};

/**
 * 提交还款；契约字段为 repaymentDraftId + paymentProofToken（一次性支付密码证明）
 * @param params 提交还款参数
 * @param idempotencyKey 幂等键，缺省时自动生成；重试同一笔还款时必须复用原键
 */
export const submitRepayment = (
  params: {
    repaymentDraftId: string;
    paymentProofToken: string;
  },
  idempotencyKey?: string,
) => {
  return request.post<{
    repaymentId: string;
    amountFen: number;
    status: string;
    createdAt: string;
    updatedAt: string;
  }>('/api/v1/credit/repayments', params, {
    headers: { 'Idempotency-Key': idempotencyKey || generateIdempotencyKey() },
  });
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
