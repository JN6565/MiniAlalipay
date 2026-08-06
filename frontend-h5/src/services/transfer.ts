import request from './request';

/** 转账草稿 */
export interface TransferDraft {
  draftId: string;
  payeeUserId: string;
  payeeNickname: string;
  payeeAccountMasked: string;
  amountFen: number;
  remark?: string;
  version: number;
  expiresAt: string;
}

/** 转账结果 */
export interface TransferResult {
  transactionId: string;
  status: string;
  amountFen: number;
  payeeNickname: string;
  createdAt: string;
}

/** 风控预检结果 */
export interface RiskCheckResult {
  riskAction: 'PASS' | 'REJECT' | 'MANUAL';
  riskMessage?: string;
  riskLevel: string;
}

/**
 * 创建转账草稿
 * @param params 转账参数
 * @returns 草稿信息
 */
export const createDraft = (params: {
  payeeUserId: string;
  amountFen: number;
  remark?: string;
}): Promise<TransferDraft> => {
  return request.post('/api/v1/transfer-drafts', {
    data: params,
  }) as unknown as Promise<TransferDraft>;
};

/**
 * 查询草稿详情
 * @param draftId 草稿ID
 * @returns 草稿信息
 */
export const getDraft = (draftId: string): Promise<TransferDraft> => {
  return request.get(`/api/v1/transfer-drafts/${draftId}`) as unknown as Promise<TransferDraft>;
};

/**
 * 更新草稿
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
): Promise<TransferDraft> => {
  return request.patch(`/api/v1/transfer-drafts/${draftId}`, {
    data: params,
  }) as unknown as Promise<TransferDraft>;
};

/**
 * 校验草稿（风控预检）
 * @param draftId 草稿ID
 * @returns 风控结果
 */
export const validateDraft = (draftId: string): Promise<RiskCheckResult> => {
  return request.post(`/api/v1/transfer-drafts/${draftId}/validate`) as unknown as Promise<RiskCheckResult>;
};

/**
 * 生成确认令牌
 * @param params 确认参数
 * @returns 确认令牌
 */
export const createConfirmation = (params: {
  subjectType: string;
  subjectId: string;
  paymentProof: string;
}): Promise<{ confirmationToken: string; expiresAt: string }> => {
  return request.post('/api/v1/confirmations', {
    data: params,
  }) as unknown as Promise<{ confirmationToken: string; expiresAt: string }>;
};

/**
 * 执行转账
 * @param params 转账参数
 * @returns 转账结果
 */
export const submitTransfer = (params: {
  draftId: string;
  confirmationToken: string;
}): Promise<TransferResult> => {
  return request.post('/api/v1/transfers', {
    data: params,
  }) as unknown as Promise<TransferResult>;
};

/**
 * 查询交易状态
 * @param transactionId 交易ID
 * @returns 交易结果
 */
export const getTransferStatus = (transactionId: string): Promise<TransferResult> => {
  return request.get(`/api/v1/transfers/${transactionId}`) as unknown as Promise<TransferResult>;
};
