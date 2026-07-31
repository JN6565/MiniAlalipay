import request from './request';

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

export interface TransferResult {
  transactionId: string;
  status: string;
  amountFen: number;
  payeeNickname: string;
  createdAt: string;
}

// 创建转账草稿
export const createDraft = (params: {
  payeeUserId: string;
  amountFen: number;
  remark?: string;
}) => {
  return request.post<TransferDraft>('/api/v1/transfer-drafts', {
    data: params,
  });
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
  return request.patch<TransferDraft>(`/api/v1/transfer-drafts/${draftId}`, {
    data: params,
  });
};

// 校验草稿（风控预检）
export const validateDraft = (draftId: string) => {
  return request.post<{
    riskAction: 'PASS' | 'REJECT' | 'MANUAL';
    riskMessage?: string;
    riskLevel: string;
  }>(`/api/v1/transfer-drafts/${draftId}/validate`);
};

// 提交转账
export const submitTransfer = (params: {
  draftId: string;
  confirmationToken: string;
}) => {
  return request.post<TransferResult>('/api/v1/transfers', {
    data: params,
  });
};

// 查询交易状态
export const getTransferStatus = (transactionId: string) => {
  return request.get<TransferResult>(`/api/v1/transfers/${transactionId}`);
};
