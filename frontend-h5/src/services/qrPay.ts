import request from './request';
import { generateIdempotencyKey } from './utils';

export interface QrPayOrder {
  qrOrderId: string;
  payeeDisplayName: string | null;
  amountFen: number;
  subject: string | null;
  expiresAt: string;
  status: string;
  transactionId: string | null;
  qrCodeUrl?: string | null;
  version: number;
}

/** 创建支持余额或 Mini 花呗支付的动态扫码收款订单。 */
export const createDynamicQrOrder = (params: {
  amountFen: number;
  subject?: string;
}): Promise<QrPayOrder> => {
  return Promise.resolve(request.post<QrPayOrder>(
    '/api/v1/qr-pay/orders',
    params,
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  )).then((response) => response as unknown as QrPayOrder);
};

// 加载H5壳（不消费令牌）
export const loadH5Shell = (token: string) => {
  return request.get('/api/v1/qr-pay/orders/by-token', { params: { t: token } });
};

// 交换令牌并获取订单
export const exchangeToken = (token: string): Promise<QrPayOrder> => {
  return Promise.resolve(request.post<QrPayOrder>('/api/v1/qr-pay/token-exchanges', { token }))
    .then((response) => response as unknown as QrPayOrder);
};

// 标记已扫码
export const markScanned = (orderId: string): Promise<QrPayOrder> => {
  return Promise.resolve(request.post<QrPayOrder>(`/api/v1/qr-pay/orders/${orderId}/scan`))
    .then((response) => response as unknown as QrPayOrder);
};

// 生成确认令牌
export const createConfirmation = (
  orderId: string,
  params: {
    version: number;
    paymentProof: string;
    fundingSource: 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD';
    cardId?: string;
  },
) : Promise<{ confirmationToken: string; expiresAt: string }> => {
  return request.post<{ confirmationToken: string; expiresAt: string }>(
    `/api/v1/qr-pay/orders/${orderId}/confirmations`,
    params,
  ) as unknown as Promise<{ confirmationToken: string; expiresAt: string }>;
};

// 提交支付
export const submitPayment = (
  orderId: string,
  params: {
    confirmationToken: string;
  },
) : Promise<{ orderId: string; transactionId: string; status: string; statusUrl: string }> => {
  return request.post<{ orderId: string; transactionId: string; status: string; statusUrl: string }>(
    `/api/v1/qr-pay/orders/${orderId}/pay`,
    params,
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  ) as unknown as Promise<{ orderId: string; transactionId: string; status: string; statusUrl: string }>;
};

// 查询订单状态
export const getOrderStatus = (orderId: string) => {
  return request.get<QrPayOrder>(`/api/v1/qr-pay/orders/${orderId}`);
};
