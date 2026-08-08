/**
 * B 端监控编码的运营展示名称。
 *
 * 服务端保留稳定编码用于筛选、审计和契约校验，页面在展示边界翻译为中文；
 * 未知编码不直接暴露给运营人员，避免内部枚举影响阅读。
 */
const QUALITY_TASK_LABELS: Record<string, string> = {
  TPLUS1: 'T+1 报表',
  'T+1': 'T+1 报表',
  TRANSACTION_COMPLETENESS: '交易完整性',
  'transaction-completeness': '交易完整性',
  RECONCILIATION: '账实对账',
  LEDGER_RECONCILIATION: '账本对账',
  INBOX: '事件消费',
};

const QUALITY_RULE_LABELS: Record<string, string> = {
  INBOX_COMPLETE: '事件消费完整性',
  EVENT_QUARANTINE_EMPTY: '隔离事件为空',
  TRANSACTION_COMPLETENESS: '交易完整性',
  'terminal-event-link': '终态事件关联',
  TERMINAL_EVENT_LINK: '终态事件关联',
  LEDGER_BALANCE: '账本借贷平衡',
  ACCOUNT_BALANCE_CONSISTENCY: '账户余额一致性',
  VOUCHER_BALANCE: '凭证借贷平衡',
  DUPLICATE_EVENT: '重复事件检查',
  TIMELINESS: '数据及时性',
  UNIQUENESS: '数据唯一性',
  COMPLETENESS: '数据完整性',
  VALIDITY: '数据合法性',
  CONSISTENCY: '数据一致性',
};

/** 将质量任务编码翻译为运营可读名称，并保留中文测试数据原样。 */
export function qualityTaskLabel(code: string | null | undefined): string {
  if (!code) return '—';
  return QUALITY_TASK_LABELS[code] ?? (/[一-鿿]/.test(code) ? code : '其他质量任务');
}

/** 将质量规则编码翻译为运营可读名称，并保留中文测试数据原样。 */
export function qualityRuleLabel(code: string | null | undefined): string {
  if (!code) return '—';
  return QUALITY_RULE_LABELS[code] ?? (/[一-鿿]/.test(code) ? code : '其他质量规则');
}

