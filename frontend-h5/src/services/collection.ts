import request from './request';

export interface PersonalCode {
  codeId: string;
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  qrCodeUrl: string;
  createdAt: string;
}

export interface CollectionRequest {
  requestId: string;
  amountFen: number;
  subject: string;
  shareUrl: string;
  qrCodeUrl: string;
  status: string;
  expiresAt: string;
  transactionId?: string;
}

export interface CollectionOrder {
  orderId: string;
  mode: 'PERSONAL_QR' | 'FIXED_REQUEST';
  payeeName: string;
  amountFen?: number;
  subject?: string;
  editable: boolean;
  status: string;
}

// 查询本人个人码
export const getMyCode = () => {
  return request.get<PersonalCode | null>('/v1/p2p-collections/codes/me');
};

// 重新生成个人码
export const regenerateCode = () => {
  return request.post<PersonalCode>(
    '/v1/p2p-collections/codes/me/regenerations',
  );
};

// 停用个人码
export const disableCode = () => {
  return request.post('/v1/p2p-collections/codes/me/disable');
};

// 创建固定收款请求
export const createRequest = (params: {
  amountFen: number;
  subject?: string;
}) => {
  return request.post<CollectionRequest>('/v1/p2p-collections/requests', {
    data: params,
  });
};

// 查询固定请求状态
export const getRequestStatus = (requestId: string) => {
  return request.get<CollectionRequest>(
    `/v1/p2p-collections/requests/${requestId}`,
  );
};

// 取消固定请求
export const cancelRequest = (requestId: string) => {
  return request.post(
    `/v1/p2p-collections/requests/${requestId}/cancel`,
  );
};

// 交换令牌
export const exchangeToken = (token: string) => {
  return request.post<CollectionOrder>(
    '/v1/p2p-collections/token-exchanges',
    { data: { token } },
  );
};

// 锁定订单金额
export const lockOrderAmount = (
  orderId: string,
  params: { amountFen: number; subject?: string },
) => {
  return request.patch(`/v1/p2p-collections/orders/${orderId}`, {
    data: params,
  });
};

// 生成确认令牌
export const createOrderConfirmation = (
  orderId: string,
  paymentPassword: string,
) => {
  return request.post<{ confirmationToken: string; expiresAt: string }>(
    `/v1/p2p-collections/orders/${orderId}/confirmations`,
    { data: { paymentPassword } },
  );
};

// 提交支付
export const submitPayment = (
  orderId: string,
  confirmationToken: string,
) => {
  return request.post<{ transactionId: string; status: string }>(
    `/v1/p2p-collections/orders/${orderId}/pay`,
    { data: { confirmationToken } },
  );
};

// 查询订单状态
export const getOrderStatus = (orderId: string) => {
  return request.get<CollectionOrder>(
    `/v1/p2p-collections/orders/${orderId}`,
  );
};
