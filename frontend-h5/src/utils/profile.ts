/** 生成个人信息页的账户名展示文本。 */
export const formatAccountName = (accountNumber?: string): string =>
  `账户名：${accountNumber || '加载中...'}`;
