-- 阶段五运营投影表；本迁移只修改 metrics_db。
-- Inbox、分析事件与聚合投影必须在同一 metrics_db 本地事务内提交，不能反向修改资金事实。
CREATE TABLE IF NOT EXISTS metrics_db.inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_event_status (status, updated_at),
    CONSTRAINT ck_inbox_event_status CHECK (status IN ('PROCESSING','DONE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.analytics_event (
    event_id CHAR(26) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(16) NULL,
    occurred_at DATETIME(3) NOT NULL,
    dimensions_json JSON NOT NULL,
    metrics_json JSON NOT NULL,
    trace_id CHAR(32) NULL,
    PRIMARY KEY (event_id),
    KEY idx_analytics_event_type (event_type, occurred_at),
    KEY idx_analytics_event_business (business_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.quarantined_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    schema_version SMALLINT UNSIGNED NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    quarantined_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3) NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_quarantined_event_status (status, quarantined_at),
    CONSTRAINT ck_quarantined_event_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.metric_definition (
    metric_code VARCHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    formula TEXT NOT NULL,
    dimensions_json JSON NOT NULL,
    owner_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    PRIMARY KEY (metric_code, version),
    KEY idx_metric_definition_status (status, effective_at),
    CONSTRAINT ck_metric_definition_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.daily_metric (
    metric_date DATE NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    dimension_hash BINARY(32) NOT NULL,
    dimensions_json JSON NOT NULL,
    value_decimal DECIMAL(24,6) NOT NULL,
    quality_status VARCHAR(16) NOT NULL,
    version INT UNSIGNED NOT NULL,
    PRIMARY KEY (metric_date, metric_code, dimension_hash, version),
    KEY idx_daily_metric_code (metric_code, metric_date),
    CONSTRAINT ck_daily_metric_quality CHECK (quality_status IN ('PENDING','PASSED','FAILED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.quality_result (
    result_id CHAR(26) NOT NULL,
    task_code VARCHAR(64) NOT NULL,
    data_date DATE NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expected_value DECIMAL(24,6) NULL,
    actual_value DECIMAL(24,6) NULL,
    evidence_json JSON NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    PRIMARY KEY (result_id),
    UNIQUE KEY uk_quality_result_rule (task_code, data_date, rule_code),
    KEY idx_quality_result_status (status, checked_at),
    CONSTRAINT ck_quality_result_status CHECK (status IN ('PASSED','FAILED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS metrics_db.monitor_alert (
    alert_id CHAR(26) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    subject_id VARCHAR(128) NULL,
    evidence_json JSON NOT NULL,
    assignee_id CHAR(26) NULL,
    last_reason VARCHAR(256) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    opened_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    PRIMARY KEY (alert_id),
    KEY idx_monitor_alert_status (status, severity, opened_at),
    CONSTRAINT ck_monitor_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
