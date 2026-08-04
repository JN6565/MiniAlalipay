-- 阶段四账本 TCC 与对账差异；本文件只修改 ledger_db。
-- 账本分支不依赖 account_db 外键，通过统一交易 ID 进行逻辑关联。
CREATE TABLE IF NOT EXISTS ledger_db.tcc_branch (
    branch_id CHAR(26) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    branch_type VARCHAR(32) NOT NULL,
    resource_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'INIT',
    rollback_type VARCHAR(16) NULL,
    barrier_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (branch_id),
    UNIQUE KEY uk_tcc_branch_resource (xid,branch_type,resource_id),
    KEY idx_ledger_tcc_transaction (transaction_id),
    KEY idx_ledger_tcc_recovery (status,updated_at),
    CONSTRAINT ck_ledger_tcc_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_ledger_tcc_status CHECK (status IN ('INIT','TRIED','CONFIRMED','CANCELLED','MANUAL_REVIEW')),
    CONSTRAINT ck_ledger_tcc_rollback CHECK (rollback_type IS NULL OR rollback_type IN ('NORMAL','EMPTY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 与 account_db 相同，安全补齐服务器初始化表缺少的金额和创建时间字段。
SET @ledger_amount_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ledger_db.tcc_branch ADD COLUMN amount_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER resource_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = 'ledger_db' AND table_name = 'tcc_branch' AND column_name = 'amount_fen'
);
PREPARE ledger_amount_stmt FROM @ledger_amount_sql;
EXECUTE ledger_amount_stmt;
DEALLOCATE PREPARE ledger_amount_stmt;

SET @ledger_created_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ledger_db.tcc_branch ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) BEFORE updated_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = 'ledger_db' AND table_name = 'tcc_branch' AND column_name = 'created_at'
);
PREPARE ledger_created_stmt FROM @ledger_created_sql;
EXECUTE ledger_created_stmt;
DEALLOCATE PREPARE ledger_created_stmt;

SET @ledger_amount_check_sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = 'ledger_db' AND table_name = 'tcc_branch'
           AND constraint_name = 'ck_ledger_tcc_amount') = 0
        AND (SELECT COUNT(*) FROM ledger_db.tcc_branch) = 0,
        'ALTER TABLE ledger_db.tcc_branch ADD CONSTRAINT ck_ledger_tcc_amount CHECK (amount_fen > 0)',
        'SELECT 1')
);
PREPARE ledger_amount_check_stmt FROM @ledger_amount_check_sql;
EXECUTE ledger_amount_check_stmt;
DEALLOCATE PREPARE ledger_amount_check_stmt;

-- 对账差异只追加，不覆盖历史；修复动作通过新冲正凭证完成。
CREATE TABLE IF NOT EXISTS ledger_db.reconciliation_diff (
    diff_id CHAR(26) NOT NULL,
    biz_date DATE NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    diff_type VARCHAR(32) NOT NULL,
    expected_json JSON NOT NULL,
    actual_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    manual_case_id CHAR(26) NULL,
    trace_id CHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3) NULL,
    PRIMARY KEY (diff_id),
    UNIQUE KEY uk_reconciliation_diff_fact (biz_date,transaction_id,diff_type),
    KEY idx_reconciliation_diff_status_created (status,created_at),
    CONSTRAINT ck_reconciliation_diff_status CHECK (status IN ('OPEN','PROCESSING','RESOLVED','IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
