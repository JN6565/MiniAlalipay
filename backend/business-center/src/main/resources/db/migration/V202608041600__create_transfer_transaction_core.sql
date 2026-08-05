-- 阶段四普通转账与统一资金交易迁移；本文件只修改 business_db。
-- 原始确认令牌不得落库，confirmation.token_digest 仅保存 SHA-256 摘要。
CREATE TABLE IF NOT EXISTS business_db.transfer_draft (
    draft_id CHAR(26) NOT NULL,
    payer_user_id CHAR(26) NOT NULL,
    payee_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    remark VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (draft_id),
    KEY idx_transfer_draft_owner (payer_user_id,status,updated_at),
    KEY idx_transfer_draft_expiry (status,expires_at),
    CONSTRAINT ck_transfer_draft_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_transfer_draft_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_transfer_draft_status CHECK (status IN ('DRAFT','VALIDATED','PENDING_CONFIRMATION','SUBMITTED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.confirmation_subject (
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    current_confirmation_id CHAR(26) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (subject_type,subject_id),
    UNIQUE KEY uk_confirmation_subject_current (current_confirmation_id),
    KEY idx_confirmation_subject_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.confirmation (
    confirmation_id CHAR(26) NOT NULL,
    token_digest BINARY(32) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    subject_hash BINARY(32) NOT NULL,
    payer_user_id CHAR(26) NOT NULL,
    payment_proof_id CHAR(26) NOT NULL,
    pay_password_version BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status='ACTIVE' THEN CONCAT(subject_type,':',subject_id) ELSE NULL END
    ) STORED,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_confirmation_token (token_digest),
    UNIQUE KEY uk_confirmation_payment_proof (payment_proof_id),
    UNIQUE KEY uk_confirmation_active_subject (active_subject_key),
    KEY idx_confirmation_owner (payer_user_id,status,pay_password_version),
    KEY idx_confirmation_expiry (status,expires_at),
    CONSTRAINT ck_confirmation_status CHECK (status IN ('ACTIVE','CONSUMED','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.fund_transaction (
    transaction_id CHAR(26) NOT NULL,
    business_type VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_order_id CHAR(26) NOT NULL,
    initiator_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    funding_source VARCHAR(16) NOT NULL,
    related_transaction_id CHAR(26) NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    risk_level VARCHAR(16) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transaction_id),
    UNIQUE KEY uk_fund_transaction_source (source_type,source_order_id),
    UNIQUE KEY uk_fund_transaction_idempotency (initiator_user_id,business_type,idempotency_key),
    KEY idx_fund_transaction_recovery (status,updated_at),
    KEY idx_fund_transaction_payee (payee_account_id,created_at),
    CONSTRAINT ck_fund_transaction_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_fund_transaction_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_fund_transaction_status CHECK (status IN ('PROCESSING','COMPENSATING','MANUAL_REVIEW','SUCCESS','REVERSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 全局事务先持久化再调用参与者，next_retry_at 为服务重启后的接管依据。
CREATE TABLE IF NOT EXISTS business_db.tcc_global (
    transaction_id CHAR(26) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transaction_id),
    UNIQUE KEY uk_tcc_global_xid (xid),
    KEY idx_tcc_global_recovery (status,next_retry_at),
    CONSTRAINT ck_tcc_global_status CHECK (status IN ('PROCESSING','COMMITTING','ROLLING_BACK','SUCCESS','CANCELLED','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.idempotency_record (
    record_id CHAR(26) NOT NULL,
    principal_key VARCHAR(128) NOT NULL,
    api_scope VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    resource_type VARCHAR(32) NULL,
    resource_id CHAR(26) NULL,
    response_json JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_idempotency_scope (principal_key,api_scope,idempotency_key),
    KEY idx_idempotency_status (status,updated_at),
    KEY idx_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.manual_case (
    case_id CHAR(26) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NULL,
    reason_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status IN ('OPEN','PROCESSING') THEN CONCAT(subject_type,':',subject_id) ELSE NULL END
    ) STORED,
    operator_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_manual_case_active_subject (active_subject_key),
    KEY idx_manual_case_status_created (status,created_at),
    KEY idx_manual_case_subject_status (subject_type,subject_id,status),
    CONSTRAINT ck_manual_case_status CHECK (status IN ('OPEN','PROCESSING','RESOLVED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 交易受理和终态变化必须与 Outbox 在同一 business_db 本地事务提交。
CREATE TABLE IF NOT EXISTS business_db.outbox_event (
    event_id CHAR(26) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id CHAR(26) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version SMALLINT UNSIGNED NOT NULL,
    business_type VARCHAR(16) NULL,
    source_type VARCHAR(32) NULL,
    source_order_id CHAR(26) NULL,
    funding_source VARCHAR(16) NULL,
    transaction_id CHAR(26) NULL,
    producer VARCHAR(32) NOT NULL,
    account_id CHAR(26) NULL,
    merchant_account_id CHAR(26) NULL,
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
    UNIQUE KEY uk_business_outbox_version (aggregate_type,aggregate_id,aggregate_version,event_type),
    KEY idx_business_outbox_publish (status,next_retry_at),
    KEY idx_business_outbox_transaction (transaction_id,event_type),
    CONSTRAINT ck_business_outbox_status CHECK (status IN ('PENDING','PUBLISHED','DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
