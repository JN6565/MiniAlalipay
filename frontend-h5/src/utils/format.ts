import dayjs from 'dayjs';

/**
 * 分转元，保留2位小数
 */
export const fenToYuan = (fen: number): string => {
  return (fen / 100).toFixed(2);
};

/**
 * 分转元（带符号）
 */
export const fenToYuanWithSign = (fen: number, direction: 'IN' | 'OUT'): string => {
  const yuan = fenToYuan(fen);
  return direction === 'IN' ? `+${yuan}` : `-${yuan}`;
};

/**
 * 格式化金额（千分位）
 */
export const formatAmount = (fen: number): string => {
  const yuan = fen / 100;
  return yuan.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

/**
 * 格式化时间
 */
export const formatTime = (dateStr: string, format: string = 'YYYY-MM-DD HH:mm:ss'): string => {
  return dayjs(dateStr).format(format);
};

/**
 * 格式化相对时间
 */
export const formatRelativeTime = (dateStr: string): string => {
  const now = dayjs();
  const target = dayjs(dateStr);
  const diffMinutes = now.diff(target, 'minute');
  const diffHours = now.diff(target, 'hour');
  const diffDays = now.diff(target, 'day');

  if (diffMinutes < 1) {
    return '刚刚';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}分钟前`;
  }
  if (diffHours < 24) {
    return `${diffHours}小时前`;
  }
  if (diffDays < 7) {
    return `${diffDays}天前`;
  }
  return formatTime(dateStr, 'MM-DD HH:mm');
};

/**
 * 脱敏手机号
 */
export const maskPhone = (phone: string): string => {
  if (!phone || phone.length < 7) {
    return phone;
  }
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
};

/**
 * 脱敏登录名
 */
export const maskAccountNumber = (name: string): string => {
  if (!name || name.length < 3) {
    return name;
  }
  return name.substring(0, 2) + '***';
};

/**
 * 姓名脱敏：保留首字符，其余以 * 遮蔽（如 吕布 → 吕*）；
 * 与后端展示边界脱敏规则一致，空值统一展示 ***
 */
export const maskName = (name?: string | null): string => {
  if (!name) return '***';
  const trimmed = name.trim();
  if (trimmed.length <= 1) return trimmed + '**';
  return trimmed[0] + '*'.repeat(trimmed.length - 1);
};

/**
 * 账号脱敏：保留前 4 位和后 4 位，中间以 **** 遮蔽（如 6296****3228）；
 * 短账号直接展示，与后端展示边界脱敏规则一致
 */
export const maskAccount = (account?: string | null): string => {
  if (!account) return '';
  if (account.length <= 8) return account;
  return `${account.slice(0, 4)}****${account.slice(-4)}`;
};

/**
 * 生成UUID
 */
export const generateUUID = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

/**
 * 倒计时格式化
 */
export const formatCountdown = (seconds: number): string => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};
