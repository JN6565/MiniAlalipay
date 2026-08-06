-- 告警规则与阈值配置，属于运营投影，不持有资金事实。
-- 阈值由管理员通过运营接口在版本 CAS 下修改，规则结构（指标、算符、级别）不可变更。
-- 种子规则对应系统分析 16.3 的 P0 告警，初始阈值为 0，表示"出现即告警"。
CREATE TABLE IF NOT EXISTS metrics_db.monitor_alert_rule (
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    operator VARCHAR(8) NOT NULL,
    threshold_value BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (rule_code),
    KEY idx_alert_rule_metric (metric_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO metrics_db.monitor_alert_rule
    (rule_code, rule_name, metric_code, severity, operator, threshold_value, enabled, version, updated_by, created_at, updated_at)
VALUES
    ('DUPLICATE_CHARGE', '重复扣款告警', 'duplicate_charge_count', 'CRITICAL', 'GT', 0, 1, 0, 'seed', NOW(3), NOW(3)),
    ('LEDGER_IMBALANCE', '借贷不平告警', 'ledger_imbalance_count', 'CRITICAL', 'GT', 0, 1, 0, 'seed', NOW(3), NOW(3)),
    ('SAGA_COMPENSATE_FAIL', 'Saga补偿失败告警', 'saga_compensate_fail_count', 'CRITICAL', 'GT', 0, 1, 0, 'seed', NOW(3), NOW(3));
