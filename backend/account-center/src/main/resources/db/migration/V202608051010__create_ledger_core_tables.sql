-- 阶段三复式账本内核迁移；本文件只修改 ledger_db。
-- 凭证先以 PREPARED 和完整分录持久化，数据库汇总验平后才允许 POSTED。
CREATE TABLE IF NOT EXISTS ledger_db.ledger_account (
    ledger_account_id CHAR(26) NOT NULL,
    owner_type VARCHAR(24) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    account_class VARCHAR(16) NOT NULL,
    normal_direction VARCHAR(8) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (ledger_account_id),
    UNIQUE KEY uk_ledger_account_owner (owner_type, owner_id, account_type, currency),
    UNIQUE KEY uk_ledger_account_code (account_code),
    KEY idx_ledger_account_owner (owner_type, owner_id),
    CONSTRAINT ck_ledger_account_owner_type CHECK (owner_type IN ('SYSTEM', 'USER', 'MERCHANT', 'CREDIT_ACCOUNT')),
    CONSTRAINT ck_ledger_account_class CHECK (account_class IN ('ASSET', 'LIABILITY', 'EQUITY')),
    CONSTRAINT ck_ledger_account_direction CHECK (normal_direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_account_status CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ledger_db.ledger_voucher (
    voucher_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    voucher_type VARCHAR(24) NOT NULL,
    reversal_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    original_voucher_id CHAR(26) NULL,
    reversal_reason VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PREPARED',
    total_debit_fen BIGINT UNSIGNED NOT NULL,
    total_credit_fen BIGINT UNSIGNED NOT NULL,
    posted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (voucher_id),
    UNIQUE KEY uk_ledger_voucher_business (transaction_id, voucher_type, reversal_no),
    KEY idx_ledger_voucher_original (original_voucher_id),
    KEY idx_ledger_voucher_status_created (status, created_at),
    CONSTRAINT fk_ledger_voucher_original FOREIGN KEY (original_voucher_id)
        REFERENCES ledger_db.ledger_voucher (voucher_id),
    CONSTRAINT ck_ledger_voucher_balance CHECK (total_debit_fen = total_credit_fen AND total_debit_fen > 0),
    CONSTRAINT ck_ledger_voucher_status CHECK (status IN ('PREPARED', 'POSTED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_ledger_voucher_reversal_reason CHECK (
        reversal_reason IS NULL OR reversal_reason IN ('BUSINESS_REFUND', 'RECONCILIATION', 'SYSTEM_CORRECTION')
    ),
    CONSTRAINT ck_ledger_voucher_reversal CHECK (
        (reversal_no = 0 AND original_voucher_id IS NULL AND reversal_reason IS NULL) OR
        (reversal_no > 0 AND original_voucher_id IS NOT NULL AND reversal_reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ledger_db.ledger_entry (
    entry_id BIGINT UNSIGNED NOT NULL,
    voucher_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    ledger_account_id CHAR(26) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    memo VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (entry_id),
    UNIQUE KEY uk_ledger_entry_sequence (voucher_id, sequence_no),
    KEY idx_ledger_entry_account_time (ledger_account_id, created_at, entry_id),
    KEY idx_ledger_entry_transaction (transaction_id),
    CONSTRAINT fk_ledger_entry_voucher FOREIGN KEY (voucher_id) REFERENCES ledger_db.ledger_voucher (voucher_id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_db.ledger_account (ledger_account_id),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount_fen > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账本过账事件与凭证状态必须在同一 ledger_db 本地事务提交。
CREATE TABLE IF NOT EXISTS ledger_db.outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (aggregate_type, aggregate_id, aggregate_version, event_type),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 历史初始化使用了较宽松的冲正引用约束；显式替换为同时校验 reversal_no 的约束。
DELIMITER $$
CREATE PROCEDURE ledger_db.upgrade_stage_three_ledger_core()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
               WHERE constraint_schema='ledger_db' AND table_name='ledger_voucher'
                 AND constraint_name='ck_ledger_voucher_reversal_reference') THEN
        ALTER TABLE ledger_db.ledger_voucher DROP CHECK ck_ledger_voucher_reversal_reference;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema='ledger_db' AND table_name='ledger_voucher'
                     AND constraint_name='ck_ledger_voucher_reversal') THEN
        ALTER TABLE ledger_db.ledger_voucher ADD CONSTRAINT ck_ledger_voucher_reversal CHECK (
            (reversal_no = 0 AND original_voucher_id IS NULL AND reversal_reason IS NULL) OR
            (reversal_no > 0 AND original_voucher_id IS NOT NULL AND reversal_reason IS NOT NULL)
        );
    END IF;
END$$
DELIMITER ;
CALL ledger_db.upgrade_stage_three_ledger_core();
DROP PROCEDURE ledger_db.upgrade_stage_three_ledger_core;
