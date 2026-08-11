declare namespace API {
  // 当前用户
  interface CurrentUser {
    userId: string;
    accountNumber: string;
    nickname: string;
    avatar?: string;
    userType: 'NORMAL' | 'MERCHANT' | 'OPERATOR' | 'ADMIN';
  }

  // 统一响应
  interface Response<T = any> {
    code: number;
    message: string;
    data: T;
    traceId: string;
  }

  // 分页参数
  interface PageParams {
    page?: number;
    pageSize?: number;
    cursor?: string;
  }

  // 分页响应
  interface PageResult<T> {
    items: T[];
    total: number;
    nextCursor?: string;
  }

  // 金额（分）
  type AmountFen = number;

  // 时间字符串
  type DateTimeString = string;

  // 交易状态
  type TransactionStatus =
    | 'PROCESSING'
    | 'SUCCESS'
    | 'FAILED'
    | 'CANCELLED'
    | 'MANUAL_REVIEW';

  // 业务类型
  type BusinessType =
    | 'TRANSFER'
    | 'QR_PAY'
    | 'CREDIT_PAY'
    | 'CREDIT_REPAY'
    | 'RECHARGE';

  // 资金来源
  type FundingSource = 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD';
}
