import request from './request';

// 生成幂等键
const generateIdempotencyKey = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
};

export interface PersonalCode {
  codeId: string;
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  collectionUrl: string;
  version: number;
}

export interface CollectionRequest {
  requestId: string;
  amountFen: number;
  subject: string;
  status: string;
  activeOrderId?: string;
  transactionId?: string;
  expiresAt: string;
  version: number;
}

export interface CollectionOrder {
  orderId: string;
  mode: 'PERSONAL_QR' | 'FIXED_REQUEST';
  payeeName?: string;
  payerName?: string;
  amountFen?: number;
  subject?: string;
  editable?: boolean;
  status: string;
  version?: number;
}

// 查询本人个人码
export const getMyCode = () => {
  return request.get<PersonalCode | null>('/api/v1/p2p-collections/codes/me');
};

// Bootstrap - 验证令牌并获取会话
export const bootstrapToken = (token: string) => {
  return request.get<void>(`/api/v1/p2p-collections/by-token?t=${token}`);
};

// 重新生成个人码
export const regenerateCode = () => {
  return request.post<PersonalCode>(
    '/api/v1/p2p-collections/codes/me/regenerations',
    {},
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
  );
};

// 停用个人码
export const disableCode = (version: number) => {
  return request.post(
    '/api/v1/p2p-collections/codes/me/disable',
    { version },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
  );
};

// 创建固定收款请求
export const createRequest = (params: {
  amountFen: number;
  subject?: string;
}) => {
  return request.post<CollectionRequest>(
    '/api/v1/p2p-collections/requests',
    params,
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
  );
};

// 查询固定请求状态
export const getRequestStatus = (requestId: string) => {
  return request.get<CollectionRequest>(
    `/api/v1/p2p-collections/requests/${requestId}`,
  );
};

// 取消固定请求
export const cancelRequest = (requestId: string, version: number) => {
  return request.post(
    `/api/v1/p2p-collections/requests/${requestId}/cancel`,
    { version },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
  );
};

// 交换令牌
export const exchangeToken = async (token: string): Promise<CollectionOrder> => {
  const res = await request.post<any>(
    '/api/v1/p2p-collections/token-exchanges',
    { token },
  );
  // 后端返回 collectionOrderId/kind，前端期望 orderId/mode
  return {
    orderId: res.collectionOrderId,
    mode: res.kind,
    payeeName: res.payeeName,
    payerName: res.payerName,
    amountFen: res.amountFen,
    subject: res.subject,
    editable: res.editable,
    status: res.status,
    version: res.version,
  };
};

// 锁定订单金额
export const lockOrderAmount = async (
  orderId: string,
  params: { version: number; amountFen: number; subject?: string },
): Promise<CollectionOrder> => {
  const res = await request.patch<any>(`/api/v1/p2p-collections/orders/${orderId}`, params);
  return {
    orderId: res.collectionOrderId,
    mode: res.kind,
    payeeName: res.payeeName,
    payerName: res.payerName,
    amountFen: res.amountFen,
    subject: res.subject,
    editable: res.editable,
    status: res.status,
    version: res.version,
  };
};

// 申请支付证明（向 user-center 验证支付密码，获取证明令牌）
export const issuePaymentProof = async (paymentPassword: string): Promise<string> => {
  const res = await request.post<any>(
    '/api/v1/payment-password/proof',
    { paymentPassword, purpose: 'COLLECTION_CONFIRM' },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
  // 后端返回 accessToken 字段
  return res.accessToken;
};

// 生成确认令牌（传入证明令牌）
export const createOrderConfirmation = (
  orderId: string,
  proofToken: string,
  version: number,
) => {
  return request.post<{ confirmationToken: string; expiresAt: string }>(
    `/api/v1/p2p-collections/orders/${orderId}/confirmations`,
    { version, paymentProof: proofToken, fundingSource: 'BALANCE' },
  );
};

// 提交支付
export const submitPayment = (
  orderId: string,
  confirmationToken: string,
) => {
  return request.post<{ collectionOrderId: string; transactionId: string; status: string; updatedAt: string }>(
    `/api/v1/p2p-collections/orders/${orderId}/pay`,
    { confirmationToken },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
  );
};

// 查询订单状态
export const getOrderStatus = async (orderId: string): Promise<CollectionOrder> => {
  const res = await request.get<any>(
    `/api/v1/p2p-collections/orders/${orderId}`,
  );
  return {
    orderId: res.collectionOrderId,
    mode: res.kind,
    payeeName: res.payeeName,
    payerName: res.payerName,
    amountFen: res.amountFen,
    subject: res.subject,
    editable: res.editable,
    status: res.status,
    version: res.version,
  };
};
