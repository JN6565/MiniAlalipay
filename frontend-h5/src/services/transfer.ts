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
  result: 'PASS';
  riskLevel: string;
  version: number;
}

const generateUUID = (): string => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
  const random = (Math.random() * 16) | 0;
  return (char === 'x' ? random : (random & 0x3) | 0x8).toString(16);
});

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
  return request.post('/api/v1/transfer-drafts', params, {
    headers: { 'Idempotency-Key': generateUUID() },
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
  return request.patch(`/api/v1/transfer-drafts/${draftId}`, params) as unknown as Promise<TransferDraft>;
};

/**
 * 校验草稿（风控预检）
 * @param draftId 草稿ID
 * @returns 风控结果
 */
export const validateDraft = (draftId: string, version: number): Promise<RiskCheckResult> => {
  return request.post(`/api/v1/transfer-drafts/${draftId}/validate`, { version }) as unknown as Promise<RiskCheckResult>;
};

/**
 * 生成确认令牌
 * @param params 确认参数
 * @returns 确认令牌
 */
export const createConfirmation = (params: {
  subjectType: string;
  subjectId: string;
  subjectVersion: number;
  paymentProof: string;
}): Promise<{ confirmationToken: string; expiresAt: string }> => {
  return request.post('/api/v1/confirmations', params) as unknown as Promise<{ confirmationToken: string; expiresAt: string }>;
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
  return request.post('/api/v1/transfers', params, {
    headers: { 'Idempotency-Key': generateUUID() },
    // 后端在事务提交后同步启动 TCC，账户与账本参与者完成前可能超过全局 10 秒超时。
    timeout: 30_000,
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
