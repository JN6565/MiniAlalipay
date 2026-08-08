/**
 * 生成幂等键，格式为 idem_<uuid>。
 */
export const generateIdempotencyKey = (): string => {
  const uuid = crypto.randomUUID?.() ?? generateFallbackUuid();
  return `idem_${uuid}`;
};

const generateFallbackUuid = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

/**
 * 校验 18 位身份证号合规性。
 *
 * <p>依次校验：基础格式（17 位数字 + 1 位数字或 X）、第 7-14 位出生日期
 * 真实存在且在今日之前。按产品要求跳过 GB 11643-1999 的 MOD 11-2
 * 校验位验证，便于演示测试使用编造号码；后端同样只校验格式。</p>
 *
 * @param idCard 身份证号（允许首尾空格，内部自动去除）
 * @returns 不合规时返回中文错误文案，合规则返回 null
 */
export const validateIdCard = (idCard: string): string | null => {
  const value = (idCard || '').trim().toUpperCase();
  if (!/^\d{17}[\dX]$/.test(value)) {
    return '身份证号格式不正确';
  }

  // 出生日期：第 7-14 位，必须为真实存在的日期且介于 1900-01-01 至今
  const year = Number(value.slice(6, 10));
  const month = Number(value.slice(10, 12));
  const day = Number(value.slice(12, 14));
  const birth = new Date(year, month - 1, day);
  const now = new Date();
  const isValidDate =
    year >= 1900 &&
    birth.getFullYear() === year &&
    birth.getMonth() === month - 1 &&
    birth.getDate() === day &&
    birth.getTime() <= now.getTime();
  if (!isValidDate) {
    return '身份证号出生日期不正确';
  }

  return null;
};

/**
 * 真实姓名展示脱敏。
 *
 * <p>规则：长度 1 原样返回；长度 2 仅保留首字（如 张*）；
 * 长度 ≥3 保留首尾、中间以 * 替代（如 张*三）。</p>
 *
 * @param name 真实姓名
 * @returns 脱敏后的姓名，空值返回空字符串
 */
export const maskRealName = (name: string): string => {
  if (!name) {
    return '';
  }
  if (name.length === 1) {
    return name;
  }
  if (name.length === 2) {
    return name[0] + '*';
  }
  return name[0] + '*'.repeat(name.length - 2) + name[name.length - 1];
};
