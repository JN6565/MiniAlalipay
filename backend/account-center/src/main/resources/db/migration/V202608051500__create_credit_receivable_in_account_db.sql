-- 在 account_db 中创建信用应收汇总表
CREATE TABLE IF NOT EXISTS account_db.credit_receivable (
    credit_account_id CHAR(26) NOT NULL,
    unbilled_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    billed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    overdue_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (credit_account_id),
    KEY idx_credit_receivable_updated (updated_at),
    CONSTRAINT ck_credit_receivable_overdue CHECK (overdue_fen <= billed_fen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
