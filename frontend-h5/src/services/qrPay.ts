import request from './request';

export interface QrPayOrder {
  orderId: string;
  merchantName: string;
  amountFen: number;
  subject: string;
  expiresAt: string;
  remainingSeconds: number;
  status: string;
}

// 加载H5壳（不消费令牌）
export const loadH5Shell = (token: string) => {
  return request.get('/api/v1/qr-pay/orders/by-token', { params: { token } });
};

// 交换令牌并获取订单
export const exchangeToken = (token: string) => {
  return request.post<QrPayOrder>('/api/v1/qr-pay/token-exchanges', {
    data: { token },
  });
};

// 标记已扫码
export const markScanned = (orderId: string) => {
  return request.post(`/v1/qr-pay/orders/${orderId}/scan`);
};

// 生成确认令牌
export const createConfirmation = (
  orderId: string,
  params: {
    paymentPassword: string;
    fundingSource: 'BALANCE' | 'MINI_CREDIT';
  },
) => {
  return request.post<{ confirmationToken: string; expiresAt: string }>(
    `/v1/qr-pay/orders/${orderId}/confirmations`,
    { data: params },
  );
};

// 提交支付
export const submitPayment = (
  orderId: string,
  params: {
    confirmationToken: string;
    fundingSource: 'BALANCE' | 'MINI_CREDIT';
  },
) => {
  return request.post<{ transactionId: string; status: string }>(
    `/v1/qr-pay/orders/${orderId}/pay`,
    { data: params },
  );
};

// 查询订单状态
export const getOrderStatus = (orderId: string) => {
  return request.get<QrPayOrder>(`/v1/qr-pay/orders/${orderId}`);
};
