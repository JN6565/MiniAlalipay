-- 工单处置与响应快照同属 business_db 本地事务，防止重复处置覆盖审计事实或返回后来状态。
CREATE TABLE IF NOT EXISTS business_db.manual_case_decision_idempotency (
    record_id CHAR(26) NOT NULL,
    operator_id CHAR(26) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    case_id CHAR(26) NULL,
    case_type VARCHAR(32) NULL,
    subject_type VARCHAR(32) NULL,
    subject_id CHAR(26) NULL,
    reason_code VARCHAR(64) NULL,
    status VARCHAR(16) NULL,
    case_operator_id CHAR(26) NULL,
    last_reason VARCHAR(500) NULL,
    evidence_reference VARCHAR(2000) NULL,
    case_version BIGINT UNSIGNED NULL,
    case_created_at DATETIME(3) NULL,
    case_updated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_manual_case_decision_idempotency (operator_id, idempotency_key),
    KEY idx_manual_case_decision_idempotency_case (case_id),
    CONSTRAINT ck_manual_case_decision_snapshot CHECK (
        (case_id IS NULL AND status IS NULL) OR (case_id IS NOT NULL AND status IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
