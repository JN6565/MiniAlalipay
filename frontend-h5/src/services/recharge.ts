import request from './request';

/** 充值订单 */
export interface RechargeOrder {
  rechargeOrderId: string;
  amountFen: number;
  status: string;
  version: number;
  createdAt: string;
}

/** 生成UUID v4 */
const generateUUID = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
};

/**
 * 创建模拟充值订单
 * @param amountFen 充值金额（分）
 * @param idempotencyKey 幂等键，超时重试时应使用同一个键
 * @returns 充值订单信息
 */
export const createRecharge = (amountFen: number, idempotencyKey?: string): Promise<RechargeOrder> => {
  const key = idempotencyKey || generateUUID();
  return request.post('/api/v1/recharges', { amountFen }, {
    headers: { 'Idempotency-Key': key },
  }) as unknown as Promise<RechargeOrder>;
};

/**
 * 查询充值订单状态
 * @param orderId 充值订单ID
 * @returns 充值订单信息
 */
export const getRechargeStatus = (orderId: string): Promise<RechargeOrder> => {
  return request.get(`/api/v1/recharges/${orderId}`) as unknown as Promise<RechargeOrder>;
};
