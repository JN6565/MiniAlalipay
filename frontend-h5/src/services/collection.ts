import request from './request';
import { generateIdempotencyKey } from './utils';
import type { AxiosResponse } from 'axios';

/**
 * 请求拦截器运行时会拆包 ApiResponse.data，axios 类型仍显示 AxiosResponse；
 * C2C 页面统一在服务层收口这个差异，避免页面误把响应壳当业务对象。
 */
const unwrap = <T>(promise: Promise<AxiosResponse<T>>): Promise<T> =>
  promise as unknown as Promise<T>;

export interface PersonalCode {
  codeId: string;
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  collectionUrl: string;
  /** 是否已开通花呗商户收款码。 */
  creditCollectionEnabled?: boolean;
  /** 手动输入收款短码（8 位纯数字），付款方可不扫码直接输入此码付款。 */
  shortCode?: string | null;
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
  /** 手动输入收款短码，随请求生成，有效期内可重读。 */
  shortCode?: string | null;
}

/** 短码兑换结果：码类型与可直接进入付款页的订单 ID。 */
export interface ShortCodeExchangeResult {
  codeType: 'PERSONAL_CODE' | 'COLLECTION_REQUEST' | 'QR_PAY_ORDER';
  orderId: string;
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
  /** 收款方是否允许使用 Mini 花呗付款；付款人自身资格由花呗摘要判断。 */
  creditPayAllowed?: boolean;
  /** 花呗不可用时服务端返回的收款方侧原因。 */
  creditPayDisabledReason?: string | null;
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
  return unwrap(request.get<PersonalCode | null>('/api/v1/p2p-collections/codes/me'));
};

// 重新生成个人码
export const regenerateCode = () => {
  return unwrap(request.post<PersonalCode>(
    '/api/v1/p2p-collections/codes/me/regenerations',
    {},
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  ));
};

// 停用个人码
export const disableCode = () => {
  return request.post('/api/v1/p2p-collections/codes/me/disable');
};

// 开通花呗商户收款码；开通后个人码和固定金额收款请求均可被 Mini 花呗付款。
export const openCreditCollection = () => {
  return unwrap(request.post<PersonalCode>(
    '/api/v1/p2p-collections/codes/me/credit-collection/open',
    {},
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  ));
};

// 创建固定收款请求
export const createRequest = (params: {
  amountFen: number;
  subject?: string;
}) => {
  return unwrap(request.post<CollectionRequest>(
    '/api/v1/p2p-collections/requests',
    params,
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  ));
};

// 查询固定请求状态
export const getRequestStatus = (requestId: string) => {
  return unwrap(request.get<CollectionRequest>(
    `/api/v1/p2p-collections/requests/${requestId}`,
  ));
};

// 查询固定请求的全部来源订单（创建时间倒序），仅请求创建者可读；
// 一码多收下收款方页面轮询该接口实时展示多笔收款记录。
export const getRequestOrders = (requestId: string) => {
  return unwrap(request.get<RequestOrderItem[]>(
    `/api/v1/p2p-collections/requests/${requestId}/orders`,
  ));
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
  return unwrap(request.post<CollectionOrder>(
    '/api/v1/p2p-collections/token-exchanges',
    { token },
  ));
};

// 兑换 8 位收款短码：等价于扫描对应二维码，服务端直接创建或绑定订单；
// 令牌只存摘要，兑换响应不返回原始令牌，前端按 orderId 直接进入付款页。
export const exchangeShortCode = (shortCode: string): Promise<ShortCodeExchangeResult> => {
  return Promise.resolve(request.post<ShortCodeExchangeResult>(
    '/api/v1/p2p-collections/short-code-exchanges',
    { shortCode },
  )).then((response) => response as unknown as ShortCodeExchangeResult);
};

// 锁定订单金额；返回锁定后的最新订单（版本 +1、不可再编辑），
// 后续签发确认令牌必须使用该版本。
export const lockOrderAmount = (
  orderId: string,
  params: { version: number; amountFen: number; subject?: string },
) => {
  return unwrap(request.patch<CollectionOrder>(`/api/v1/p2p-collections/orders/${orderId}`, params));
};

// 生成确认令牌（传入证明令牌）
export const createOrderConfirmation = (
  orderId: string,
  proofToken: string,
  version: number,
  fundingSource: 'BALANCE' | 'BANK_CARD' | 'MINI_CREDIT',
  cardId?: string,
) => {
  return unwrap(request.post<{ confirmationToken: string; expiresAt: string }>(
    `/api/v1/p2p-collections/orders/${orderId}/confirmations`,
    { version, paymentProof: proofToken, fundingSource, cardId },
  ));
};

// 提交支付
export const submitPayment = (
  orderId: string,
  confirmationToken: string,
  cardId?: string,
) => {
  return unwrap(request.post<{ collectionOrderId: string; transactionId: string; status: string; updatedAt: string }>(
    `/api/v1/p2p-collections/orders/${orderId}/pay`,
    { confirmationToken, cardId },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  ));
};

// 查询订单状态
export const getOrderStatus = (orderId: string) => {
  return unwrap(request.get<CollectionOrder>(
    `/api/v1/p2p-collections/orders/${orderId}`,
  ));
};
