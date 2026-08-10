import type { AxiosResponse } from 'axios';
import request from './request';

/**
 * 响应拦截器已拆包 ApiResponse 直接返回 data，但 axios 类型签名仍为 AxiosResponse；
 * 此处按运行时真实行为声明为业务数据类型，避免页面侧出现类型不匹配。
 */
const unwrap = <T>(promise: Promise<AxiosResponse<T>>): Promise<T> =>
  promise as unknown as Promise<T>;

/** 银行卡类型：DEBIT 借记卡（储蓄卡），CREDIT 信用卡。 */
export type BankCardType = 'DEBIT' | 'CREDIT';

/** 银行卡绑定状态：ACTIVE 已绑定，UNBOUND 已解绑（终态）。 */
export type BankCardStatus = 'ACTIVE' | 'UNBOUND';

/** 银行卡掩码视图：服务端只返回掩码字段，完整卡号不会出现在前端。 */
export interface BankCard {
  cardId: string;
  bankCode: string;
  bankName: string;
  cardType: BankCardType;
  cardLast4: string;
  holderMasked: string;
  idCardMasked: string;
  phoneMasked: string;
  /** 虚拟余额（分），充值增加、提现/支付扣减，与账户余额独立。 */
  balanceFen: number;
  isDefault: boolean;
  status: BankCardStatus;
  boundAt: string;
}

/** 绑卡请求：完整卡号与三要素明文仅提交一次，前端不做任何落地存储。 */
export interface BindBankCardPayload {
  cardNumber: string;
  holderName: string;
  idCard: string;
  phone: string;
}

/** 银行卡注册状态：REGISTERED 已注册可绑定（含解绑后释放回的状态），BOUND 已绑定。 */
export type RegisteredCardStatus = 'REGISTERED' | 'BOUND';

/** 银行卡注册响应：注册时返回生成的完整卡号。 */
export interface RegisteredCard {
  registrationId: string;
  bankCode: string;
  bankName: string;
  cardType: BankCardType;
  cardNumber?: string; // 仅注册响应时返回
  cardBin: string;
  cardLast4: string;
  status: RegisteredCardStatus;
  createdAt: string;
}

/** 银行卡注册请求：选择银行 + 三要素。 */
export interface RegisterBankCardPayload {
  bankCode: string;
  holderName: string;
  idCard: string;
  phone: string;
}

// 查询本人银行卡列表（仅已绑定，默认卡在前）
export const getBankCards = (): Promise<BankCard[]> =>
  unwrap(request.get<BankCard[]>('/api/v1/bank-cards'));

// 绑定银行卡（基于注册记录的绑卡流程，需先完成身份绑定和银行卡注册）
export const bindBankCard = (payload: BindBankCardPayload): Promise<BankCard> =>
  unwrap(request.post<BankCard>('/api/v1/bank-cards', payload));

// 注册银行卡（选银行 + 三要素 → 自动生成卡号）
export const registerBankCard = (payload: RegisterBankCardPayload): Promise<RegisteredCard> =>
  unwrap(request.post<RegisteredCard>('/api/v1/bank-card-registrations', payload));

// 查询本人已注册但未绑定的卡列表
export const getRegisteredCards = (): Promise<RegisteredCard[]> =>
  unwrap(request.get<RegisteredCard[]>('/api/v1/bank-card-registrations'));

// 查询银行卡详情（全掩码字段）
export const getBankCardDetail = (cardId: string): Promise<BankCard> =>
  unwrap(request.get<BankCard>(`/api/v1/bank-cards/${cardId}`));

// 设为默认卡（已是默认卡时服务端幂等返回）
export const setDefaultBankCard = (cardId: string): Promise<BankCard> =>
  unwrap(request.put<BankCard>(`/api/v1/bank-cards/${cardId}/default`));

// 解绑银行卡（软删为 UNBOUND 终态）
export const unbindBankCard = (cardId: string): Promise<void> =>
  unwrap(request.delete<void>(`/api/v1/bank-cards/${cardId}`));

/** 查询银行卡虚拟余额（分）。 */
export const getBankCardBalance = (cardId: string): Promise<{ balanceFen: number }> =>
  unwrap(request.get<{ balanceFen: number }>(`/api/v1/bank-cards/${cardId}/balance`));

/** 生成 UUID v4 作为幂等键。 */
const generateIdempotencyKey = (): string =>
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });

/** 银行卡充值请求：金额（分）+ 支付密码 + 幂等键。 */
export interface BankCardRechargePayload {
  amountFen: number;
  paymentPassword: string;
  idempotencyKey?: string;
}

/** 银行卡提现请求：金额（分）+ 支付密码 + 幂等键。 */
export interface BankCardWithdrawPayload {
  amountFen: number;
  paymentPassword: string;
  idempotencyKey?: string;
}

/** 发起银行卡充值：银行卡虚拟余额减少，账户余额同步增加。 */
export const rechargeBankCard = (cardId: string, payload: BankCardRechargePayload): Promise<any> =>
  unwrap(request.post(`/api/v1/bank-cards/${cardId}/recharge`, {
    amountFen: payload.amountFen,
    paymentPassword: payload.paymentPassword,
    idempotencyKey: payload.idempotencyKey || generateIdempotencyKey(),
  }));

/** 发起银行卡提现：银行卡虚拟余额增加，账户余额同步减少。 */
export const withdrawBankCard = (cardId: string, payload: BankCardWithdrawPayload): Promise<any> =>
  unwrap(request.post(`/api/v1/bank-cards/${cardId}/withdraw`, {
    amountFen: payload.amountFen,
    paymentPassword: payload.paymentPassword,
    idempotencyKey: payload.idempotencyKey || generateIdempotencyKey(),
  }));

/** 银行卡交易明细项：充值/提现历史记录。 */
export interface BankCardTransaction {
  transactionId: string;
  /** BANK_CARD_RECHARGE 充值（卡→账户），BANK_CARD_WITHDRAW 提现（账户→卡）。 */
  businessType: 'BANK_CARD_RECHARGE' | 'BANK_CARD_WITHDRAW';
  amountFen: number;
  status: string;
  createdAt: string;
}

/** 查询银行卡交易明细（充值/提现历史），按时间倒序。 */
export const getBankCardTransactions = (cardId: string, limit = 20): Promise<BankCardTransaction[]> =>
  unwrap(request.get<BankCardTransaction[]>(`/api/v1/bank-cards/${cardId}/transactions`, {
    params: { limit },
  }));

/** 将分转换为元字符串展示，如 123456 → "1,234.56"。 */
export const formatBalance = (balanceFen: number): string => {
  const yuan = (balanceFen / 100).toFixed(2);
  return yuan.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
};

/** 前端 BIN 字典：与后端字典保持主要银行一致，用于输入时实时识别发卡行。 */
export const BIN_TABLE: Array<{ bin: string; bankCode: string; bankName: string; cardType: BankCardType }> = [
  { bin: '621226', bankCode: 'ICBC', bankName: '中国工商银行', cardType: 'DEBIT' },
  { bin: '622202', bankCode: 'ICBC', bankName: '中国工商银行', cardType: 'DEBIT' },
  { bin: '622208', bankCode: 'ICBC', bankName: '中国工商银行', cardType: 'CREDIT' },
  { bin: '621700', bankCode: 'CCB', bankName: '中国建设银行', cardType: 'DEBIT' },
  { bin: '622280', bankCode: 'CCB', bankName: '中国建设银行', cardType: 'CREDIT' },
  { bin: '622848', bankCode: 'ABC', bankName: '中国农业银行', cardType: 'DEBIT' },
  { bin: '621661', bankCode: 'BOC', bankName: '中国银行', cardType: 'DEBIT' },
  { bin: '621483', bankCode: 'CMB', bankName: '招商银行', cardType: 'DEBIT' },
  { bin: '622575', bankCode: 'CMB', bankName: '招商银行', cardType: 'CREDIT' },
  { bin: '622262', bankCode: 'BCM', bankName: '交通银行', cardType: 'DEBIT' },
  { bin: '621098', bankCode: 'PSBC', bankName: '中国邮政储蓄银行', cardType: 'DEBIT' },
  { bin: '622188', bankCode: 'PSBC', bankName: '中国邮政储蓄银行', cardType: 'DEBIT' },
];

/** 按前 6 位 BIN 识别发卡行；位数不足或不在字典内返回 null。 */
export const identifyBank = (normalizedNumber: string) => {
  if (normalizedNumber.length < 6) return null;
  return BIN_TABLE.find((item) => normalizedNumber.startsWith(item.bin)) || null;
};

/** Luhn（模 10）校验：与后端规则一致，提交前本地预检，减少无效请求。 */
export const luhnValid = (digits: string): boolean => {
  if (!/^\d{16,19}$/.test(digits)) return false;
  let sum = 0;
  let doubleNext = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let digit = Number(digits[i]);
    if (doubleNext) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    doubleNext = !doubleNext;
  }
  return sum % 10 === 0;
};
