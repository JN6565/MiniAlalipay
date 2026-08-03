-- Mini 花呗信用子域表结构迁移。
-- 表定义与 deploy/mysql/init/00-create-schemas.sql 中的信用表保持一致。
-- 使用 CREATE TABLE IF NOT EXISTS 保证与 Docker 初始化脚本共存时不冲突。
-- 信用表分布在 account_db 和 ledger_db 两个 Schema：
--   account_db: credit_account, credit_freeze, credit_repayment_draft
--   ledger_db: credit_receivable, credit_purchase, credit_bill, credit_bill_item,
--             credit_repayment, credit_repayment_allocation, credit_repayment_allocation_detail,
--             credit_job_run

-- ============================================================
-- account_db 中的信用表
-- ============================================================

-- Mini 花呗额度账户。额度不是余额，必须始终满足已用额度与冻结额度之和不超过总额度。
CREATE TABLE IF NOT EXISTS account_db.credit_account (
    credit_account_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    total_limit_fen BIGINT UNSIGNED NOT NULL DEFAULT 500000,
    used_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    frozen_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    suspend_reason VARCHAR(32) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (credit_account_id),
    UNIQUE KEY uk_credit_account_user (user_id),
    KEY idx_credit_account_status_updated (status, updated_at),
    CONSTRAINT ck_credit_account_limit CHECK (total_limit_fen = 500000),
    CONSTRAINT ck_credit_account_usage CHECK (used_fen + frozen_fen <= total_limit_fen),
    CONSTRAINT ck_credit_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用支付 TCC 冻结事实。Confirm 转为已用额度，Cancel 只能释放一次且不得形成应收。
CREATE TABLE IF NOT EXISTS account_db.credit_freeze (
    credit_freeze_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    credit_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'FROZEN',
    branch_xid VARCHAR(128) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (credit_freeze_id),
    UNIQUE KEY uk_credit_freeze_transaction_account (transaction_id, credit_account_id),
    KEY idx_credit_freeze_account_status (credit_account_id, status),
    KEY idx_credit_freeze_status_updated (status, updated_at),
    CONSTRAINT ck_credit_freeze_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_credit_freeze_status CHECK (status IN ('FROZEN', 'CONFIRMED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用还款草稿。固化还款金额与分配摘要，确认令牌必须绑定同一快照。
CREATE TABLE IF NOT EXISTS account_db.credit_repayment_draft (
    repayment_draft_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    credit_account_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    allocation_snapshot JSON NOT NULL,
    allocation_hash BINARY(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (repayment_draft_id),
    KEY idx_credit_repayment_draft_user_status_time (user_id, status, created_at),
    KEY idx_credit_repayment_draft_status_expire (status, expires_at),
    CONSTRAINT ck_credit_repayment_draft_amount CHECK (
        amount_fen BETWEEN 1 AND 5000000
    ),
    CONSTRAINT ck_credit_repayment_draft_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'CONSUMED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- ledger_db 中的信用表
-- ============================================================

-- 信用应收汇总事实。必须满足已用额度等于未出账、已出账与逾期应收的合计口径。
CREATE TABLE IF NOT EXISTS ledger_db.credit_receivable (
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

-- 逐笔信用消费事实。每笔成功 CREDIT_PAY 最多生成一条，退款通过状态与冲正事实处理。
CREATE TABLE IF NOT EXISTS ledger_db.credit_purchase (
    purchase_id CHAR(26) NOT NULL,
    credit_transaction_id CHAR(26) NOT NULL,
    credit_account_id CHAR(26) NOT NULL,
    qr_order_id CHAR(26) NOT NULL,
    merchant_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    repaid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    refunded_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    outstanding_fen BIGINT UNSIGNED GENERATED ALWAYS AS (
        amount_fen - repaid_fen - refunded_fen
    ) STORED,
    refund_transaction_id CHAR(26) NULL,
    billing_status VARCHAR(16) NOT NULL DEFAULT 'UNBILLED',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    occurred_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (purchase_id),
    UNIQUE KEY uk_credit_purchase_transaction (credit_transaction_id),
    UNIQUE KEY uk_credit_purchase_refund_transaction (refund_transaction_id),
    KEY idx_credit_purchase_account_status_time (
        credit_account_id, billing_status, occurred_at
    ),
    KEY idx_credit_purchase_qr_order (qr_order_id),
    CONSTRAINT ck_credit_purchase_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_credit_purchase_applied CHECK (repaid_fen + refunded_fen <= amount_fen),
    CONSTRAINT ck_credit_purchase_status CHECK (
        billing_status IN ('UNBILLED', 'BILLED', 'REPAID', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 月度信用账单。账单原始金额必须等于已还、已冲销和未还金额之和。
CREATE TABLE IF NOT EXISTS ledger_db.credit_bill (
    bill_id CHAR(26) NOT NULL,
    credit_account_id CHAR(26) NOT NULL,
    `period` CHAR(7) NOT NULL,
    statement_date DATE NOT NULL,
    due_at DATETIME(3) NOT NULL,
    total_fen BIGINT UNSIGNED NOT NULL,
    paid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reversed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    outstanding_fen BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (bill_id),
    UNIQUE KEY uk_credit_bill_account_period (credit_account_id, `period`),
    KEY idx_credit_bill_status_due (status, due_at),
    CONSTRAINT ck_credit_bill_amount CHECK (
        total_fen = paid_fen + reversed_fen + outstanding_fen AND total_fen > 0
    ),
    CONSTRAINT ck_credit_bill_status CHECK (
        status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账单与信用消费的不可变关联。purchase_id 唯一保证一笔消费最多进入一个账期。
CREATE TABLE IF NOT EXISTS ledger_db.credit_bill_item (
    bill_id CHAR(26) NOT NULL,
    purchase_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    allocated_paid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reversed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (bill_id, purchase_id),
    UNIQUE KEY uk_credit_bill_item_purchase (purchase_id),
    CONSTRAINT ck_credit_bill_item_amount CHECK (
        amount_fen > 0 AND allocated_paid_fen + reversed_fen <= amount_fen
    ),
    CONSTRAINT ck_credit_bill_item_status CHECK (
        status IN ('ACTIVE', 'REPAID', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用还款事实。绑定还款草稿和统一资金交易，避免重复确认产生多笔还款。
CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment (
    repayment_id CHAR(26) NOT NULL,
    repayment_draft_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    credit_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (repayment_id),
    UNIQUE KEY uk_credit_repayment_draft (repayment_draft_id),
    UNIQUE KEY uk_credit_repayment_transaction (transaction_id),
    KEY idx_credit_repayment_account_status_time (credit_account_id, status, created_at),
    CONSTRAINT ck_credit_repayment_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_credit_repayment_status CHECK (
        status IN ('PROCESSING', 'SUCCESS', 'CANCELLED', 'MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 还款分配计划。Try 阶段固化分配顺序与金额，Confirm 阶段不得重新计算。
CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment_allocation (
    repayment_id CHAR(26) NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (repayment_id, sequence_no),
    UNIQUE KEY uk_credit_repayment_allocation_target (
        repayment_id, target_type, target_id
    ),
    KEY idx_credit_repayment_allocation_target (target_type, target_id),
    CONSTRAINT ck_credit_repayment_allocation_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_credit_repayment_allocation_type CHECK (
        target_type IN ('OVERDUE_BILL', 'BILL', 'UNBILLED_PURCHASE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 还款分配明细。逐笔指向消费及可选账单，父分配金额必须等于其明细合计。
CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment_allocation_detail (
    repayment_id CHAR(26) NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    detail_no SMALLINT UNSIGNED NOT NULL,
    purchase_id CHAR(26) NOT NULL,
    bill_id CHAR(26) NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (repayment_id, sequence_no, detail_no),
    UNIQUE KEY uk_credit_repayment_detail_purchase (repayment_id, purchase_id),
    KEY idx_credit_repayment_detail_purchase (purchase_id),
    KEY idx_credit_repayment_detail_bill_purchase (bill_id, purchase_id),
    CONSTRAINT ck_credit_repayment_detail_amount CHECK (amount_fen > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用批处理运行记录。业务日期唯一约束保证出账或到期任务重复触发时幂等续跑。
CREATE TABLE IF NOT EXISTS ledger_db.credit_job_run (
    run_id CHAR(26) NOT NULL,
    job_type VARCHAR(16) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    cursor_credit_account_id CHAR(26) NULL,
    trigger_type VARCHAR(16) NOT NULL,
    triggered_by_user_id CHAR(26) NULL,
    request_digest BINARY(32) NOT NULL,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_code VARCHAR(32) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_credit_job_type_date (job_type, business_date),
    KEY idx_credit_job_status_updated (status, updated_at),
    KEY idx_credit_job_type_status_date (job_type, status, business_date),
    CONSTRAINT ck_credit_job_type CHECK (job_type IN ('STATEMENT', 'DUE_CHECK')),
    CONSTRAINT ck_credit_job_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'MANUAL_REVIEW')
    ),
    CONSTRAINT ck_credit_job_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_credit_job_trigger_actor CHECK (
        (trigger_type = 'SCHEDULED' AND triggered_by_user_id IS NULL) OR
        (trigger_type = 'MANUAL' AND triggered_by_user_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
