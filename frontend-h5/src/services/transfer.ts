import request from './request';

export interface TransferDraft {
  draftId: string;
  payeeUserId: string;
  amountFen: number;
  remark?: string;
  status: string;
  version: number;
  expiresAt: string;
}

export interface TransferResult {
  transactionId: string;
  status: string;
  amountFen: number;
  payeeNickname: string;
  createdAt: string;
}

export interface ValidationResult {
  result: 'PASS' | 'MANUAL';
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  version: number;
}

export interface IssuedConfirmation {
  confirmationToken: string;
  subjectHash: string;
  expiresAt: string;
}

// 创建转账草稿；幂等键由 request 拦截器统一生成
export const createDraft = (params: {
  payeeUserId: string;
  amountFen: number;
  remark?: string;
}) => {
  return request.post<TransferDraft>('/api/v1/transfer-drafts', params);
};

// 查询草稿
export const getDraft = (draftId: string) => {
  return request.get<TransferDraft>(`/api/v1/transfer-drafts/${draftId}`);
};

// 更新草稿
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

// 校验草稿（风控预检）
export const validateDraft = (draftId: string, version: number) => {
  return request.post<ValidationResult>(`/api/v1/transfer-drafts/${draftId}/validate`, {
    version,
  });
};

// 签发确认令牌；契约要求确认主体为 subjectType + subjectId，当前仅支持 TRANSFER_DRAFT
export const issueConfirmation = (draftId: string, paymentProof: string, subjectVersion: number) => {
  return request.post<IssuedConfirmation>('/api/v1/confirmations', {
    subjectType: 'TRANSFER_DRAFT',
    subjectId: draftId,
    subjectVersion,
    paymentProof,
  });
};

// 提交转账；幂等键由 request 拦截器统一生成
export const submitTransfer = (params: {
  draftId: string;
  confirmationToken: string;
}) => {
  return request.post<TransferResult>('/api/v1/transfers', params);
};

// 查询交易状态
export const getTransferStatus = (transactionId: string) => {
  return request.get<TransferResult>(`/api/v1/transfers/${transactionId}`);
};
