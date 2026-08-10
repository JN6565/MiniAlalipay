/** AI 转账确认卡片使用的金额边界，所有业务值均为整数分。 */
export const MAX_TRANSFER_AMOUNT_FEN = 5_000_000;

/**
 * 将用户输入的元金额解析为整数分。
 *
 * <p>这里只处理展示边界的字符串，不使用 JavaScript 小数进行金额计算；
 * 超过两位小数或格式不合法时返回 null。</p>
 */
export function parseYuanToFen(value: string): number | null {
  const normalized = value.trim();
  if (!/^\d+(?:\.\d{0,2})?$/.test(normalized)) return null;
  const [yuan, jiaoFen = ''] = normalized.split('.');
  const fen = Number(yuan) * 100 + Number(jiaoFen.padEnd(2, '0') || '0');
  return Number.isSafeInteger(fen) ? fen : null;
}

/** 返回确认卡片应阻止提交的中文原因；通过时返回 null。 */
export function getTransferAmountError(
  amountFen: number | null,
  availableBalanceFen: number | null,
): string | null {
  if (amountFen == null || amountFen <= 0) return '请输入有效金额';
  if (amountFen > MAX_TRANSFER_AMOUNT_FEN) {
    return '单笔转账金额不能超过 50,000.00 元';
  }
  if (availableBalanceFen != null && amountFen > availableBalanceFen) {
    return '转账金额不能超过当前可用余额';
  }
  return null;
}
