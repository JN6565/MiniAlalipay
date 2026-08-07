// API基础地址
export const API_BASE_URL = 'http://localhost:8080';

// 金额范围
export const AMOUNT_MIN = 0.01;
export const AMOUNT_MAX = 50000;

// 密码规则
export const LOGIN_PASSWORD_MIN_LENGTH = 8;
export const LOGIN_PASSWORD_MAX_LENGTH = 32;
export const PAYMENT_PASSWORD_LENGTH = 6;

// 交易状态
export const TRANSACTION_STATUS = {
  PROCESSING: 'PROCESSING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  MANUAL_REVIEW: 'MANUAL_REVIEW',
} as const;

// 交易状态中文映射
export const TRANSACTION_STATUS_TEXT: Record<string, string> = {
  PROCESSING: '处理中',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已撤销',
  MANUAL_REVIEW: '人工审核中',
};

// 业务类型中文映射
export const BUSINESS_TYPE_TEXT: Record<string, string> = {
  TRANSFER: '转账',
  QR_PAY: '扫码支付',
  CREDIT_PAY: '信用支付',
  CREDIT_REPAY: '信用还款',
  RECHARGE: '充值',
};

// 资金来源中文映射
export const FUNDING_SOURCE_TEXT: Record<string, string> = {
  BALANCE: '虚拟余额',
  MINI_CREDIT: 'Mini花呗',
};

// 账单状态
export const BILL_STATUS_TEXT: Record<string, string> = {
  OPEN: '待还款',
  PARTIALLY_PAID: '部分还款',
  PAID: '已还清',
  OVERDUE: '已逾期',
};

// 风险动作
export const RISK_ACTION = {
  PASS: 'PASS',
  REJECT: 'REJECT',
  MANUAL: 'MANUAL',
} as const;

// 个人码状态
export const CODE_STATUS_TEXT: Record<string, string> = {
  ACTIVE: '正常',
  DISABLED: '已停用',
  REVOKED: '已撤销',
};

// 固定请求状态
export const REQUEST_STATUS_TEXT: Record<string, string> = {
  OPEN: '进行中',
  PROCESSING: '处理中',
  SUCCESS: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
  MANUAL_REVIEW: '人工审核中',
};

// 缓存时间（毫秒）
export const CACHE_TIME = {
  STALE_TIME: 30 * 1000,
  CACHE_TIME: 5 * 60 * 1000,
} as const;

// 充值限额
export const DAILY_RECHARGE_LIMIT_FEN = 25000000; // 每日限额 250,000 元
export const DAILY_RECHARGE_COUNT = 5; // 每日充值次数上限

// 轮询间隔（毫秒）
export const POLL_INTERVAL = {
  ORDER_STATUS: 2000,
  TRANSFER_STATUS: 3000,
} as const;
