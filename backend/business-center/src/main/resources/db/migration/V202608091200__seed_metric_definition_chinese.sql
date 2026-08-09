-- T+1 报表指标口径中文种子：为报表页可展示的指标提供中文名称、计量单位与计算公式。
-- 仅在指标码已存在时更新名称/单位/公式/状态，避免覆盖人工后续调整的口径。
INSERT INTO metrics_db.metric_definition
    (metric_code, version, name, unit, formula, dimensions_json, owner_id, status, effective_at)
VALUES
    ('transaction.accepted', 1, '交易受理笔数', '笔',
     '统计业务日内交易受理（transaction.accepted）分析事件数量',
     '{}', '01JMA8KZ000000000000000000', 'ACTIVE', '2026-08-09 12:00:00'),
    ('transaction.status.changed', 1, '交易状态变更次数', '次',
     '统计业务日内交易状态变更（transaction.status.changed）分析事件数量',
     '{}', '01JMA8KZ000000000000000000', 'ACTIVE', '2026-08-09 12:00:00'),
    ('reconciliation.diff.detected', 1, '对账差异数', '笔',
     '统计业务日内对账差异（reconciliation.diff.detected）分析事件数量',
     '{}', '01JMA8KZ000000000000000000', 'ACTIVE', '2026-08-09 12:00:00'),
    ('risk.decision.created', 1, '风控决策数', '笔',
     '统计业务日内风控决策（risk.decision.created）分析事件数量',
     '{}', '01JMA8KZ000000000000000000', 'ACTIVE', '2026-08-09 12:00:00')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), unit = VALUES(unit), formula = VALUES(formula), status = VALUES(status);
