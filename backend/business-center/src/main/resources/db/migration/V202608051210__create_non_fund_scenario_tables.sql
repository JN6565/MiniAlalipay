-- 阶段五非资金场景表；本迁移只修改 business_db，不创建交易、余额、冻结或账本数据。
-- 原始二维码和收款令牌不得落库，所有 token_digest 均只保存服务端计算的 SHA-256 摘要。
CREATE TABLE IF NOT EXISTS business_db.qr_pay_order (
    qr_order_id CHAR(26) NOT NULL,
    payee_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    payer_user_id CHAR(26) NULL,
    payer_account_id CHAR(26) NULL,
    transaction_id CHAR(26) NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (qr_order_id),
    UNIQUE KEY uk_qr_pay_order_transaction (transaction_id),
    KEY idx_qr_pay_order_payee (payee_account_id, status, created_at),
    KEY idx_qr_pay_order_expiry (status, expires_at),
    CONSTRAINT ck_qr_pay_order_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_qr_pay_order_status CHECK (status IN ('CREATED','SCANNED','PENDING_CONFIRMATION','RISK_REVIEW','PROCESSING','COMPENSATING','MANUAL_REVIEW','SUCCESS','REJECTED','CANCELLED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 一次交换只绑定一个 H5 会话，摘要唯一约束避免同一令牌被不同订单或会话重放。
CREATE TABLE IF NOT EXISTS business_db.qr_pay_token (
    token_digest BINARY(32) NOT NULL,
    qr_order_id CHAR(26) NOT NULL,
    bootstrap_session_hash BINARY(32) NULL,
    h5_session_id CHAR(26) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (token_digest),
    UNIQUE KEY uk_qr_pay_token_order (qr_order_id),
    UNIQUE KEY uk_qr_pay_token_h5_session (h5_session_id),
    KEY idx_qr_pay_token_expiry (status, expires_at),
    CONSTRAINT ck_qr_pay_token_status CHECK (status IN ('ACTIVE','BOUND','EXPIRED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- active_owner_key 只在 ACTIVE 时有值，保证每个收款用户同时最多一个有效个人码。
CREATE TABLE IF NOT EXISTS business_db.personal_collection_code (
    code_id CHAR(26) NOT NULL,
    owner_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    token_digest BINARY(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_owner_key CHAR(26) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN owner_user_id ELSE NULL END
    ) STORED,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    PRIMARY KEY (code_id),
    UNIQUE KEY uk_personal_collection_code_token (token_digest),
    UNIQUE KEY uk_personal_collection_code_active_owner (active_owner_key),
    KEY idx_personal_collection_code_owner (owner_user_id, created_at),
    KEY idx_personal_collection_code_status (status, updated_at),
    CONSTRAINT ck_personal_collection_code_status CHECK (status IN ('ACTIVE','REPLACED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.collection_request (
    request_id CHAR(26) NOT NULL,
    requester_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    token_digest BINARY(32) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    active_order_id CHAR(26) NULL,
    transaction_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_collection_request_token (token_digest),
    UNIQUE KEY uk_collection_request_transaction (transaction_id),
    KEY idx_collection_request_expiry (status, expires_at),
    KEY idx_collection_request_active_order (active_order_id),
    KEY idx_collection_request_owner (requester_user_id, created_at),
    CONSTRAINT ck_collection_request_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_collection_request_status CHECK (status IN ('OPEN','RESERVED','CLOSED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.collection_order (
    order_id CHAR(26) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    code_id CHAR(26) NULL,
    request_id CHAR(26) NULL,
    payer_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    payee_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    h5_session_id CHAR(26) NULL,
    amount_fen BIGINT UNSIGNED NULL,
    subject VARCHAR(50) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    transaction_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_collection_order_h5_session (h5_session_id),
    UNIQUE KEY uk_collection_order_transaction (transaction_id),
    KEY idx_collection_order_request (request_id, status),
    KEY idx_collection_order_code (code_id, status),
    KEY idx_collection_order_payer (payer_user_id, created_at),
    KEY idx_collection_order_payee (payee_user_id, created_at),
    CONSTRAINT ck_collection_order_source CHECK ((mode = 'PERSONAL_QR' AND code_id IS NOT NULL AND request_id IS NULL) OR (mode = 'FIXED_REQUEST' AND request_id IS NOT NULL AND code_id IS NULL)),
    CONSTRAINT ck_collection_order_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_collection_order_status CHECK (status IN ('DRAFT','PENDING_CONFIRMATION','PROCESSING','CANCELLED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.risk_decision (
    decision_id CHAR(26) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NULL,
    rule_version VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason_code VARCHAR(32) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (decision_id),
    KEY idx_risk_decision_subject (subject_type, subject_id, created_at),
    KEY idx_risk_decision_transaction (transaction_id),
    CONSTRAINT ck_risk_decision_action CHECK (action IN ('PASS','REJECT','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 既有阶段四工单表保留，向前补足处置证据，并使状态名与领域模型的 CLAIMED 一致。
ALTER TABLE business_db.manual_case
    ADD COLUMN last_reason VARCHAR(256) NULL AFTER operator_id,
    ADD COLUMN evidence_reference VARCHAR(256) NULL AFTER last_reason;
ALTER TABLE business_db.manual_case DROP CHECK ck_manual_case_status;
-- 阶段四的 PROCESSING 表示已领取；先前向转换历史值，再收紧到阶段五工单状态机。
UPDATE business_db.manual_case SET status = 'CLAIMED' WHERE status = 'PROCESSING';
ALTER TABLE business_db.manual_case
    ADD CONSTRAINT ck_manual_case_status CHECK (status IN ('OPEN','CLAIMED','RESOLVED','CLOSED'));
ALTER TABLE business_db.manual_case
    MODIFY active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status IN ('OPEN','CLAIMED') THEN CONCAT(subject_type,':',subject_id) ELSE NULL END
    ) STORED;
