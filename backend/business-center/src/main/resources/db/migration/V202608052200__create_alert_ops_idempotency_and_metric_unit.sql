-- 阶段五监控运维：告警处置与响应快照同属 metrics_db 本地事务，防止重复处置覆盖审计事实或返回后来状态。
CREATE TABLE IF NOT EXISTS metrics_db.alert_ops_idempotency (
    record_id CHAR(26) NOT NULL,
    operator_id CHAR(26) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    alert_id CHAR(26) NULL,
    alert_type VARCHAR(64) NULL,
    severity VARCHAR(8) NULL,
    status VARCHAR(16) NULL,
    alert_operator_id CHAR(26) NULL,
    last_reason VARCHAR(256) NULL,
    alert_version BIGINT UNSIGNED NULL,
    alert_created_at DATETIME(3) NULL,
    alert_updated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_alert_ops_idempotency (operator_id, idempotency_key),
    KEY idx_alert_ops_idempotency_alert (alert_id),
    CONSTRAINT ck_alert_ops_snapshot CHECK (
        (alert_id IS NULL AND status IS NULL) OR (alert_id IS NOT NULL AND status IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 指标口径对外展示需要单位字段，向前迁移补齐默认单位，避免运营报表缺失单位解释。
ALTER TABLE metrics_db.metric_definition ADD COLUMN unit VARCHAR(32) NOT NULL DEFAULT 'count';
