-- 阶段四账户 TCC 屏障；本文件只修改 account_db，保证分支幂等、空回滚和防悬挂。
CREATE TABLE IF NOT EXISTS account_db.tcc_branch (
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
    KEY idx_account_tcc_transaction (transaction_id),
    KEY idx_account_tcc_recovery (status,updated_at),
    CONSTRAINT ck_account_tcc_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_account_tcc_status CHECK (status IN ('INIT','TRIED','CONFIRMED','CANCELLED','MANUAL_REVIEW')),
    CONSTRAINT ck_account_tcc_rollback CHECK (rollback_type IS NULL OR rollback_type IN ('NORMAL','EMPTY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Docker 初始化脚本早于阶段四字段定义；通过 information_schema 生成向前兼容 DDL。
-- 历史分支无法推导原金额，因此用 0 明确标记为待人工核验，禁止静默当作新分支重放。
SET @account_amount_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE account_db.tcc_branch ADD COLUMN amount_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER resource_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = 'account_db' AND table_name = 'tcc_branch' AND column_name = 'amount_fen'
);
PREPARE account_amount_stmt FROM @account_amount_sql;
EXECUTE account_amount_stmt;
DEALLOCATE PREPARE account_amount_stmt;

SET @account_created_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE account_db.tcc_branch ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) BEFORE updated_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = 'account_db' AND table_name = 'tcc_branch' AND column_name = 'created_at'
);
PREPARE account_created_stmt FROM @account_created_sql;
EXECUTE account_created_stmt;
DEALLOCATE PREPARE account_created_stmt;

SET @account_amount_check_sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = 'account_db' AND table_name = 'tcc_branch'
           AND constraint_name = 'ck_account_tcc_amount') = 0
        AND (SELECT COUNT(*) FROM account_db.tcc_branch) = 0,
        'ALTER TABLE account_db.tcc_branch ADD CONSTRAINT ck_account_tcc_amount CHECK (amount_fen > 0)',
        'SELECT 1')
);
PREPARE account_amount_check_stmt FROM @account_amount_check_sql;
EXECUTE account_amount_check_stmt;
DEALLOCATE PREPARE account_amount_check_stmt;
