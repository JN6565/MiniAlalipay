-- 最终交易投影只消费业务事件，用交易号去重后向看板和 T+1 报表提供同一统计口径。
-- 该表属于 metrics_db，不回查 business_db 或 account-center 私有表，避免报表查询跨库耦合。
CREATE TABLE metrics_db.monitoring_transaction_final_projection (
    transaction_id CHAR(26) NOT NULL,
    amount_fen BIGINT NOT NULL,
    business_type VARCHAR(16) NULL,
    status VARCHAR(16) NOT NULL,
    accepted_at DATETIME(3) NULL,
    terminal_at DATETIME(3) NULL,
    source_occurred_at DATETIME(3) NOT NULL,
    source_event_id CHAR(26) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transaction_id),
    KEY idx_monitoring_transaction_final_terminal (status, terminal_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
