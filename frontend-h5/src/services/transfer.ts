import request from './request';
import { generateIdempotencyKey } from './utils';

/** 转账草稿（与后端 DraftResponse 字段一致；查询详情时附带收款方脱敏展示信息） */
export interface TransferDraft {
  draftId: string;
  payeeUserId: string;
  amountFen: number;
  remark?: string;
  status: string;
  version: number;
  expiresAt: string;
  payeeMaskedName?: string | null; // 收款人脱敏展示名（仅查询详情时返回）
  payeeMaskedAccountNumber?: string | null; // 收款人脱敏账户号（同上）
}

/** 转账结果（展示名与账号均由后端脱敏后返回） */
export interface TransferResult {
  transactionId: string;
  businessType: string;
  status: string;
  amountFen: number;
  payerUserId: string;
  payerDisplayName?: string | null;
  payerMaskedAccountNumber?: string | null;
  payeeUserId: string;
  payeeDisplayName?: string | null;
  payeeMaskedAccountNumber?: string | null;
  remark?: string | null;
  statusUrl: string;
  createdAt: string;
  updatedAt: string;
}

/** 风控预检结果 */
export interface ValidationResult {
  result: 'PASS' | 'MANUAL';
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  version: number;
}

/** 确认令牌签发结果 */
export interface IssuedConfirmation {
  confirmationToken: string;
  subjectHash: string;
  expiresAt: string;
}

/**
 * 创建转账草稿；契约要求携带 Idempotency-Key，超时可传入同一幂等键重试
 * @param params 转账参数
 * @param idempotencyKey 幂等键，缺省时自动生成；重试同一笔转账时必须复用原键
 * @returns 草稿信息
 */
export const createDraft = (
  params: {
    payeeUserId: string;
    amountFen: number;
    remark?: string;
  },
  idempotencyKey?: string,
) => {
  return request.post<TransferDraft>('/api/v1/transfer-drafts', params, {
    headers: { 'Idempotency-Key': idempotencyKey || generateIdempotencyKey() },
  });
};

/**
 * 查询草稿详情
 * @param draftId 草稿ID
 * @param signal 可选 AbortSignal，用于取消请求
 * @returns 草稿信息
 */
export const getDraft = (draftId: string, signal?: AbortSignal) => {
  return request.get<TransferDraft>(`/api/v1/transfer-drafts/${draftId}`, { signal });
};

/**
 * 更新草稿（CAS 版本控制）
 * @param draftId 草稿ID
 * @param params 更新参数
 * @returns 更新后的草稿
 */
export const updateDraft = (
  draftId: string,
  params: {
    amountFen?: number;
    remark?: string;
    version: number;
  },
) => {
  return request.patch<TransferDraft>(`/api/v1/transfer-drafts/${draftId}`, params);
};

/**
 * 校验草稿（风控预检）；后端要求携带客户端读取到的草稿版本
 * @param draftId 草稿ID
 * @param version 草稿 CAS 版本
 * @param signal 可选 AbortSignal，用于取消请求
 * @returns 风控结果和新版本
 */
export const validateDraft = (draftId: string, version: number, signal?: AbortSignal) => {
  return request.post<ValidationResult>(`/api/v1/transfer-drafts/${draftId}/validate`, {
    version,
  }, { signal });
};

/**
 * 签发确认令牌；契约要求四个字段齐全：subjectType + subjectId + subjectVersion + paymentProof
 * @param draftId 草稿ID
 * @param paymentProof 支付证明（不得写入日志、URL 或浏览器存储）
 * @param subjectVersion 校验后的草稿版本
 * @returns 一次性确认令牌（两分钟有效）
 */
export const issueConfirmation = (draftId: string, paymentProof: string, subjectVersion: number) => {
  return request.post<IssuedConfirmation>('/api/v1/confirmations', {
    subjectType: 'TRANSFER_DRAFT',
    subjectId: draftId,
    subjectVersion,
    paymentProof,
  });
};

/**
 * 执行转账；契约要求携带 Idempotency-Key，超时可传入同一幂等键重试
 * @param params 转账参数
 * @param idempotencyKey 幂等键，缺省时自动生成；重试同一笔转账时必须复用原键
 * @returns 转账结果
 */
export const submitTransfer = (
  params: {
    draftId: string;
    confirmationToken: string;
  },
  idempotencyKey?: string,
) => {
  return request.post<TransferResult>('/api/v1/transfers', params, {
    headers: { 'Idempotency-Key': idempotencyKey || generateIdempotencyKey() },
  });
};

/**
 * 查询交易状态
 * @param transactionId 交易ID
 * @returns 交易结果
 */
export const getTransferStatus = (transactionId: string) => {
  return request.get<TransferResult>(`/api/v1/transfers/${transactionId}`);
};

/**
 * 验密即支付（合并提交）：一次请求完成支付密码验证、确认令牌签发与转账受理，
 * 替代原 proof → confirmations → transfers 三次串行调用，降低点击支付到受理完成的耗时。
 *
 * 支付密码仅通过请求体传输，不得写入日志、URL、浏览器存储或埋点。
 *
 * @param draftId 草稿ID
 * @param version 风控预检返回的草稿版本
 * @param paymentPassword 支付密码（6 位数字）
 * @param idempotencyKey 幂等键，缺省时自动生成；重试同一笔转账时必须复用原键
 * @returns 转账受理结果（初次通常为 PROCESSING，终态由结果页轮询）
 */
export const submitTransferWithPassword = (
  draftId: string,
  version: number,
  paymentPassword: string,
  idempotencyKey?: string,
) => {
  return request.post<TransferResult>(
    '/api/v1/transfers/submit-with-password',
    { draftId, version, paymentPassword },
    { headers: { 'Idempotency-Key': idempotencyKey || generateIdempotencyKey() } },
  );
};
