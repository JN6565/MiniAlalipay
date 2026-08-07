import request from './request';

/** 转账草稿（与后端 DraftResponse 字段一致；收款人昵称和账号不在草稿响应中） */
export interface TransferDraft {
  draftId: string;
  payeeUserId: string;
  amountFen: number;
  remark?: string;
  status: string;
  version: number;
  expiresAt: string;
}

/** 转账结果 */
export interface TransferResult {
  transactionId: string;
  businessType: string;
  status: string;
  amountFen: number;
  statusUrl: string;
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
 * 创建转账草稿；幂等键由 request 拦截器统一生成
 * @param params 转账参数
 * @returns 草稿信息
 */
export const createDraft = (params: {
  payeeUserId: string;
  amountFen: number;
  remark?: string;
}) => {
  return request.post<TransferDraft>('/api/v1/transfer-drafts', params);
};

/**
 * 查询草稿详情
 * @param draftId 草稿ID
 * @returns 草稿信息
 */
export const getDraft = (draftId: string) => {
  return request.get<TransferDraft>(`/api/v1/transfer-drafts/${draftId}`);
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
 * @returns 风控结果和新版本
 */
export const validateDraft = (draftId: string, version: number) => {
  return request.post<ValidationResult>(`/api/v1/transfer-drafts/${draftId}/validate`, {
    version,
  });
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
 * 执行转账；幂等键由 request 拦截器统一生成
 * @param params 转账参数
 * @returns 转账结果
 */
export const submitTransfer = (params: {
  draftId: string;
  confirmationToken: string;
}) => {
  return request.post<TransferResult>('/api/v1/transfers', params);
};

/**
 * 查询交易状态
 * @param transactionId 交易ID
 * @returns 交易结果
 */
export const getTransferStatus = (transactionId: string) => {
  return request.get<TransferResult>(`/api/v1/transfers/${transactionId}`);
};
