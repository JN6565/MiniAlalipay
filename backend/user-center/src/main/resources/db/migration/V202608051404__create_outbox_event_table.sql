-- Outbox 事件表。
-- 保存待可靠发布事件，业务事实与事件在同一本地事务提交。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 10.3 节。

CREATE TABLE IF NOT EXISTS user_db.outbox_event (
    event_id CHAR(26) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id CHAR(26) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    business_type VARCHAR(16) NULL,
    source_type VARCHAR(32) NULL,
    source_order_id CHAR(26) NULL,
    transaction_id CHAR(26) NULL,
    producer VARCHAR(32) NOT NULL,
    user_id_hash BINARY(32) NULL,
    trace_id CHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_outbox_aggregate (aggregate_type, aggregate_id, aggregate_version, event_type),
    KEY idx_outbox_status_retry (status, next_retry_at),
    KEY idx_outbox_transaction (transaction_id, event_type),
    KEY idx_outbox_occurred (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
