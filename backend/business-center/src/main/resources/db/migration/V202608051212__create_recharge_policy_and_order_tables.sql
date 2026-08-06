-- 阶段五受控模拟充值表；本迁移只修改 business_db，不直接增加余额或写入账本。
-- 当前活动策略由产品确认：单笔 5000000 分，单日累计 25000000 分，单日最多 5 次。
CREATE TABLE IF NOT EXISTS business_db.recharge_policy (
    policy_id CHAR(26) NOT NULL,
    policy_code VARCHAR(32) NOT NULL,
    single_limit_fen BIGINT UNSIGNED NOT NULL,
    daily_limit_fen BIGINT UNSIGNED NOT NULL,
    daily_count_limit INT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
    ) STORED,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    effective_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_recharge_policy_version (policy_code, version),
    UNIQUE KEY uk_recharge_policy_active (active_slot),
    KEY idx_recharge_policy_status (status, effective_at),
    CONSTRAINT ck_recharge_policy_limits CHECK (single_limit_fen BETWEEN 1 AND 5000000 AND daily_limit_fen >= single_limit_fen AND daily_count_limit > 0),
    CONSTRAINT ck_recharge_policy_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 同一用户同一业务日通过版本 CAS 原子预占处理中额度；未知资金结果不能释放该占用。
CREATE TABLE IF NOT EXISTS business_db.recharge_daily_usage (
    user_id CHAR(26) NOT NULL,
    business_date DATE NOT NULL,
    processing_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    processing_count INT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, business_date),
    CONSTRAINT ck_recharge_daily_usage_amount CHECK (processing_fen >= 0 AND success_fen >= 0),
    CONSTRAINT ck_recharge_daily_usage_count CHECK (processing_count >= 0 AND success_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_db.recharge_order (
    recharge_order_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    target_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    business_date DATE NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'SIMULATED',
    policy_id CHAR(26) NOT NULL,
    policy_version BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING_CHANNEL',
    transaction_id CHAR(26) NULL,
    reject_reason_code VARCHAR(32) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (recharge_order_id),
    UNIQUE KEY uk_recharge_order_transaction (transaction_id),
    KEY idx_recharge_order_user_day (user_id, business_date, status, created_at),
    KEY idx_recharge_order_status (status, updated_at),
    CONSTRAINT ck_recharge_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_recharge_order_channel CHECK (channel = 'SIMULATED'),
    CONSTRAINT ck_recharge_order_status CHECK (status IN ('PENDING_CHANNEL','PROCESSING','SUCCESS','REJECTED','CANCELLED','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 初始策略只在尚无活动版本时写入；后续调整必须插入新版本并切换活动槽位，不能修改历史订单快照。
INSERT INTO business_db.recharge_policy
    (policy_id, policy_code, single_limit_fen, daily_limit_fen, daily_count_limit, status, version, effective_at, created_at, updated_at)
SELECT '01K22RECHARGEPOLICY0000001', 'SIMULATED_RECHARGE', 5000000, 25000000, 5, 'ACTIVE', 0,
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM business_db.recharge_policy WHERE status = 'ACTIVE');
