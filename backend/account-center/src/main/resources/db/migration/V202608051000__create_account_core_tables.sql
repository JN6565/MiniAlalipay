-- 阶段三账户与余额内核迁移；本文件只修改 account_db。
-- 金额以分保存，余额与冻结状态只能通过版本和业务条件原子更新。
CREATE TABLE IF NOT EXISTS account_db.account (
    account_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    registration_id CHAR(26) NOT NULL,
    account_type VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_user_type_currency (user_id, account_type, currency),
    UNIQUE KEY uk_account_registration (registration_id),
    KEY idx_account_status_updated (status, updated_at),
    CONSTRAINT ck_account_type CHECK (account_type IN ('PERSONAL', 'MERCHANT')),
    CONSTRAINT ck_account_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS account_db.account_balance (
    account_id CHAR(26) NOT NULL,
    available_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    frozen_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    KEY idx_account_balance_updated (updated_at),
    CONSTRAINT fk_account_balance_account FOREIGN KEY (account_id) REFERENCES account_db.account (account_id),
    CONSTRAINT ck_account_balance_non_negative CHECK (available_fen >= 0 AND frozen_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS account_db.freeze_record (
    freeze_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    account_id CHAR(26) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'FROZEN',
    branch_xid VARCHAR(128) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (freeze_id),
    UNIQUE KEY uk_freeze_business (transaction_id, account_id, purpose),
    KEY idx_freeze_account_status (account_id, status),
    KEY idx_freeze_status_updated (status, updated_at),
    CONSTRAINT fk_freeze_account FOREIGN KEY (account_id) REFERENCES account_db.account (account_id),
    CONSTRAINT ck_freeze_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_freeze_status CHECK (status IN ('FROZEN', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_freeze_purpose CHECK (purpose IN ('TRANSFER_OUT', 'CREDIT_REPAYMENT', 'REFUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Docker 历史初始化可能已建表，以下过程只补缺失列、索引和约束。
DELIMITER $$
CREATE PROCEDURE account_db.upgrade_stage_three_account_core()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema='account_db' AND table_name='account' AND column_name='registration_id') THEN
        ALTER TABLE account_db.account ADD COLUMN registration_id CHAR(26) NULL AFTER user_id;
        UPDATE account_db.account SET registration_id = account_id WHERE registration_id IS NULL;
        ALTER TABLE account_db.account MODIFY COLUMN registration_id CHAR(26) NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema='account_db' AND table_name='account' AND index_name='uk_account_registration') THEN
        ALTER TABLE account_db.account ADD UNIQUE KEY uk_account_registration (registration_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema='account_db' AND table_name='account' AND constraint_name='ck_account_type') THEN
        ALTER TABLE account_db.account ADD CONSTRAINT ck_account_type CHECK (account_type IN ('PERSONAL', 'MERCHANT'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema='account_db' AND table_name='account_balance'
                     AND constraint_name='ck_account_balance_non_negative') THEN
        ALTER TABLE account_db.account_balance ADD CONSTRAINT ck_account_balance_non_negative
            CHECK (available_fen >= 0 AND frozen_fen >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema='account_db' AND table_name='freeze_record'
                     AND constraint_name='ck_freeze_purpose') THEN
        ALTER TABLE account_db.freeze_record ADD CONSTRAINT ck_freeze_purpose
            CHECK (purpose IN ('TRANSFER_OUT', 'CREDIT_REPAYMENT', 'REFUND'));
    END IF;
END$$
DELIMITER ;
CALL account_db.upgrade_stage_three_account_core();
DROP PROCEDURE account_db.upgrade_stage_three_account_core;
