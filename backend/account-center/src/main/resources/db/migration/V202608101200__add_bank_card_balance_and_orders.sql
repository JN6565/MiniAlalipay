-- 银行卡虚拟余额与充值提现功能迁移；本文件只修改 account_db。
-- 设计要点：
-- 1. bank_card 增加 balance_fen 字段，支持独立虚拟余额（与账户余额分离）
-- 2. 新增充值订单表、提现订单表、日累计用量表
-- 3. 金额单位为分（BIGINT UNSIGNED），单笔限额 0.01-50000.00 元（1-500000000 分）
-- 4. 日累计充值/提现各 50000.00 元（500000000 分）

-- 1. bank_card 表增加余额字段
ALTER TABLE account_db.bank_card
ADD COLUMN balance_fen BIGINT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '虚拟余额（分），充值增加、提现/支付扣减，与账户余额独立'
  AFTER phone_masked;

-- 2. 银行卡充值订单表
CREATE TABLE IF NOT EXISTS account_db.bank_card_recharge_order (
    recharge_order_id CHAR(26) NOT NULL COMMENT '充值订单 ID，26 位 ULID',
    user_id CHAR(26) NOT NULL COMMENT '充值用户 ID',
    card_id CHAR(26) NOT NULL COMMENT '目标银行卡 ID',
    amount_fen BIGINT UNSIGNED NOT NULL COMMENT '充值金额（分）',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '幂等键，防止重复充值',
    transaction_id CHAR(26) NULL COMMENT '关联交易 ID，TCC 全局事务标识',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后更新时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (recharge_order_id),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_user_status (user_id, status),
    KEY idx_card (card_id),
    CONSTRAINT ck_recharge_status CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED','CANCELLED')),
    CONSTRAINT ck_recharge_amount CHECK (amount_fen BETWEEN 1 AND 500000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='银行卡充值订单，记录从银行卡余额充值到账户余额的交易';

-- 3. 银行卡提现订单表
CREATE TABLE IF NOT EXISTS account_db.bank_card_withdraw_order (
    withdraw_order_id CHAR(26) NOT NULL COMMENT '提现订单 ID，26 位 ULID',
    user_id CHAR(26) NOT NULL COMMENT '提现用户 ID',
    card_id CHAR(26) NOT NULL COMMENT '源银行卡 ID',
    amount_fen BIGINT UNSIGNED NOT NULL COMMENT '提现金额（分）',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '幂等键，防止重复提现',
    transaction_id CHAR(26) NULL COMMENT '关联交易 ID，TCC 全局事务标识',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后更新时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (withdraw_order_id),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_user_status (user_id, status),
    KEY idx_card (card_id),
    CONSTRAINT ck_withdraw_status CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED','CANCELLED')),
    CONSTRAINT ck_withdraw_amount CHECK (amount_fen BETWEEN 1 AND 500000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='银行卡提现订单，记录从账户余额提现到银行卡余额的交易';

-- 4. 银行卡日累计用量表
CREATE TABLE IF NOT EXISTS account_db.bank_card_daily_usage (
    usage_id CHAR(26) NOT NULL COMMENT '用量记录 ID',
    user_id CHAR(26) NOT NULL COMMENT '用户 ID',
    card_id CHAR(26) NOT NULL COMMENT '银行卡 ID',
    usage_date DATE NOT NULL COMMENT '使用日期',
    recharge_total_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日累计充值金额（分）',
    withdraw_total_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日累计提现金额（分）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (usage_id),
    UNIQUE KEY uk_user_card_date (user_id, card_id, usage_date),
    KEY idx_date (usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='银行卡日累计充值/提现用量，用于限额校验';
