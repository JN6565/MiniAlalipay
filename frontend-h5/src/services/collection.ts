import request from './request';
import { generateIdempotencyKey } from './utils';

export interface PersonalCode {
  codeId: string;
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  collectionUrl: string;
  version?: number;
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
  /** 收款二维码引导地址，仅创建响应携带。 */
  collectionUrl?: string;
}

export interface CollectionOrder {
  /** 订单 ID，后端字段为 collectionOrderId。 */
  collectionOrderId: string;
  /** 订单类型：个人码或固定金额请求，后端字段为 kind。 */
  kind: 'PERSONAL_QR' | 'FIXED_REQUEST';
  payeeName: string;
  /** 付款人脱敏姓名，后端已脱敏，仅展示用。 */
  payerName?: string;
  amountFen?: number;
  subject?: string;
  editable: boolean;
  status: string;
  /** 受理资金后关联的统一交易 ID，结果页展示用。 */
  transactionId?: string;
  version?: number;
}

/** 收款方订单列表条目：一码多收下同一请求的多笔订单，付款人姓名已脱敏。 */
export interface RequestOrderItem {
  collectionOrderId: string;
  kind: 'PERSONAL_QR' | 'FIXED_REQUEST';
  amountFen?: number;
  subject?: string;
  status: string;
  transactionId?: string;
  expiresAt?: string;
  version?: number;
  payeeName?: string;
  payerName?: string;
  createdAt?: string;
}

// 查询本人个人码
export const getMyCode = () => {
  return request.get<PersonalCode | null>('/api/v1/p2p-collections/codes/me');
};

// 重新生成个人码
export const regenerateCode = () => {
  return request.post<PersonalCode>(
    '/api/v1/p2p-collections/codes/me/regenerations',
    {},
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
};

// 停用个人码
export const disableCode = () => {
  return request.post('/api/v1/p2p-collections/codes/me/disable');
};

// 创建固定收款请求
export const createRequest = (params: {
  amountFen: number;
  subject?: string;
}) => {
  return request.post<CollectionRequest>(
    '/api/v1/p2p-collections/requests',
    params,
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
};

// 查询固定请求状态
export const getRequestStatus = (requestId: string) => {
  return request.get<CollectionRequest>(
    `/api/v1/p2p-collections/requests/${requestId}`,
  );
};

// 查询固定请求的全部来源订单（创建时间倒序），仅请求创建者可读；
// 一码多收下收款方页面轮询该接口实时展示多笔收款记录。
export const getRequestOrders = (requestId: string) => {
  return request.get<RequestOrderItem[]>(
    `/api/v1/p2p-collections/requests/${requestId}/orders`,
  );
};

// 取消固定请求
export const cancelRequest = (requestId: string, version: number) => {
  return request.post(
    `/api/v1/p2p-collections/requests/${requestId}/cancel`,
    { version },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
};

// 匿名扫码建立 H5 引导会话（后端返回 204，不消费令牌）；
// 后续令牌交换、锁定金额与确认支付必须携带该会话 Cookie。
export const bootstrapSession = (token: string) => {
  return request.get('/api/v1/p2p-collections/by-token', { params: { t: token } });
};

// 交换令牌
export const exchangeToken = (token: string) => {
  return request.post<CollectionOrder>(
    '/api/v1/p2p-collections/token-exchanges',
    { token },
  );
};

// 锁定订单金额；返回锁定后的最新订单（版本 +1、不可再编辑），
// 后续签发确认令牌必须使用该版本。
export const lockOrderAmount = (
  orderId: string,
  params: { version: number; amountFen: number; subject?: string },
) => {
  return request.patch<CollectionOrder>(`/api/v1/p2p-collections/orders/${orderId}`, params);
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
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
};

// 查询订单状态
export const getOrderStatus = (orderId: string) => {
  return request.get<CollectionOrder>(
    `/api/v1/p2p-collections/orders/${orderId}`,
  );
};
