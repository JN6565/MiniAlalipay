import { qualityRuleLabel, qualityTaskLabel } from '@/utils/opsLabels';

describe('监控编码中文展示', () => {
  it('翻译已知质量任务和规则编码', () => {
    expect(qualityTaskLabel('TPLUS1')).toBe('T+1 报表');
    expect(qualityRuleLabel('EVENT_QUARANTINE_EMPTY')).toBe('隔离事件为空');
  });

  it('保留中文服务端名称并为未知英文编码提供中文兜底', () => {
    expect(qualityTaskLabel('交易完整性')).toBe('交易完整性');
    expect(qualityRuleLabel('UNKNOWN_RULE')).toBe('其他质量规则');
  });
});
