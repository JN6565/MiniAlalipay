-- MiniAlalipay 本地 MySQL 8.0 初始化脚本。
-- 跨库业务 ID 仅作逻辑引用；外键只约束同一服务拥有的 Schema。
-- 本脚本用于首次初始化本地开发环境，统一创建逻辑库、开发账号、业务表、索引与约束。
-- 六个逻辑库按服务职责隔离；同一 MySQL 实例部署不改变各服务的数据所有权边界。
-- CREATE TABLE IF NOT EXISTS 只保证重复执行不报错，不会更新已经存在的表结构或注释。
-- 已有数据库需要变更时，应新增向前迁移，不得依赖重复执行本初始化脚本。

CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS business_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS account_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ledger_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS agent_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS metrics_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 本地开发账号仅用于容器内联调；生产环境必须改用按服务、按 Schema 最小授权的独立账号。
CREATE USER IF NOT EXISTS 'mini_app'@'%' IDENTIFIED BY 'mini_app_dev_only';
GRANT ALL PRIVILEGES ON user_db.* TO 'mini_app'@'%';
GRANT ALL PRIVILEGES ON business_db.* TO 'mini_app'@'%';
GRANT ALL PRIVILEGES ON account_db.* TO 'mini_app'@'%';
GRANT ALL PRIVILEGES ON ledger_db.* TO 'mini_app'@'%';
GRANT ALL PRIVILEGES ON agent_db.* TO 'mini_app'@'%';
GRANT ALL PRIVILEGES ON metrics_db.* TO 'mini_app'@'%';

-- 用户中心。
-- 负责身份、凭证、联系人和角色事实，不保存账户余额或资金交易事实。
USE user_db;

-- 用户主档。注册期间的状态与版本用于支撑跨服务开户恢复，不能据此直接推导资金账户状态。
CREATE TABLE IF NOT EXISTS app_user (
    user_id CHAR(26) NOT NULL,
    login_name VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    phone_tail CHAR(4) NULL,
    identity_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    id_card VARCHAR(32) NULL COMMENT '身份证号掩码，绑定身份后保存，如 3301**********1234',
    id_card_hash BINARY(32) NULL COMMENT '身份证号明文哈希，用于绑卡时三要素交叉比对',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_login_name (login_name),
    KEY idx_app_user_nickname_status (nickname, status),
    KEY idx_app_user_status_created (status, created_at),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 登录与支付密码凭证。这里只保存安全摘要和锁定状态，禁止保存任何明文密码。
CREATE TABLE IF NOT EXISTS credential (
    user_id CHAR(26) NOT NULL,
    login_password_hash VARCHAR(255) NOT NULL,
    payment_password_hash VARCHAR(255) NULL,
    login_fail_count INT UNSIGNED NOT NULL DEFAULT 0,
    pay_fail_count INT UNSIGNED NOT NULL DEFAULT 0,
    login_lock_until DATETIME(3) NULL,
    pay_lock_until DATETIME(3) NULL,
    pay_password_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_credential_login_lock (login_lock_until),
    KEY idx_credential_pay_lock (pay_lock_until),
    CONSTRAINT fk_credential_user FOREIGN KEY (user_id) REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 一次性支付密码证明。令牌只保存摘要，并绑定密码版本以便改密后立即撤销旧授权。
CREATE TABLE IF NOT EXISTS payment_proof (
    proof_id CHAR(26) NOT NULL,
    token_digest BINARY(32) NOT NULL,
    user_id CHAR(26) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    pay_password_version BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (proof_id),
    UNIQUE KEY uk_payment_proof_token (token_digest),
    KEY idx_payment_proof_user_status_expire (user_id, status, expires_at),
    CONSTRAINT fk_payment_proof_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_payment_proof_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 常用收款人关系。仅由成功转账事实形成，不表示好友关系，也不能授予对方任何权限。
CREATE TABLE IF NOT EXISTS contact (
    owner_user_id CHAR(26) NOT NULL,
    payee_user_id CHAR(26) NOT NULL,
    alias VARCHAR(64) NULL,
    success_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_success_at DATETIME(3) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (owner_user_id, payee_user_id),
    KEY idx_contact_owner_sort (owner_user_id, pinned, hidden, last_success_at),
    KEY idx_contact_payee (payee_user_id),
    CONSTRAINT fk_contact_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_contact_payee FOREIGN KEY (payee_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_contact_distinct_user CHECK (owner_user_id <> payee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户角色分配。MERCHANT 为历史兼容值，MVP 不据此建立独立商户身份或客户端。
CREATE TABLE IF NOT EXISTS role_assignment (
    user_id CHAR(26) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    KEY idx_role_assignment_role (role_code),
    CONSTRAINT fk_role_assignment_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_role_assignment_role CHECK (
        role_code IN ('USER', 'MERCHANT', 'OPERATOR', 'ADMIN', 'OBSERVER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户中心写接口的幂等受理记录。同一主体、接口范围和幂等键只能对应同一请求摘要。
CREATE TABLE IF NOT EXISTS idempotency_record (
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
    UNIQUE KEY uk_idempotency_scope (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_recovery (status, updated_at),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户中心事件消费去重表。业务投影与 DONE 状态必须在同一本地事务内提交。
CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_status_updated (status, updated_at),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户中心安全审计日志。只保存脱敏详情，禁止记录密码、原始令牌和完整账号。
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    detail_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_target_time (target_type, target_id, occurred_at),
    KEY idx_audit_trace (trace_id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'OPERATOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户中心可靠事件表。业务事实与事件同事务写入，发布失败只能重试，不能重复生成事实。
CREATE TABLE IF NOT EXISTS outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心账户与额度模块。
-- 仅本模块可以维护账户余额、冻结金额和信用额度；其他服务必须通过契约调用。
USE account_db;

-- 虚拟资金账户主档。user_id 是跨 Schema 逻辑引用，不创建到 user_db 的外键。
CREATE TABLE IF NOT EXISTS account (
    account_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    registration_id CHAR(26) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_owner_type_currency (user_id, account_type, currency),
    UNIQUE KEY uk_account_registration (registration_id),
    KEY idx_account_status_updated (status, updated_at),
    CONSTRAINT ck_account_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户余额事实。available_fen 与 frozen_fen 均以分为单位，变更必须使用 version 条件更新。
CREATE TABLE IF NOT EXISTS account_balance (
    account_id CHAR(26) NOT NULL,
    available_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    frozen_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    KEY idx_account_balance_updated (updated_at),
    CONSTRAINT fk_account_balance_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_balance_non_negative CHECK (available_fen >= 0 AND frozen_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 余额 TCC 冻结事实。唯一键防止同一交易、账户和用途重复冻结，释放操作必须幂等。
CREATE TABLE IF NOT EXISTS freeze_record (
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
    UNIQUE KEY uk_freeze_transaction_account_purpose (transaction_id, account_id, purpose),
    KEY idx_freeze_account_status (account_id, status),
    KEY idx_freeze_status_updated (status, updated_at),
    CONSTRAINT fk_freeze_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_freeze_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_freeze_status CHECK (status IN ('FROZEN', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_freeze_purpose CHECK (purpose IN ('TRANSFER_OUT', 'CREDIT_REPAYMENT', 'REFUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Mini 花呗额度账户。额度不是余额，必须始终满足已用额度与冻结额度之和不超过总额度。
CREATE TABLE IF NOT EXISTS credit_account (
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
CREATE TABLE IF NOT EXISTS credit_freeze (
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
    CONSTRAINT fk_credit_freeze_account FOREIGN KEY (credit_account_id)
        REFERENCES credit_account (credit_account_id),
    CONSTRAINT ck_credit_freeze_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_credit_freeze_status CHECK (status IN ('FROZEN', 'CONFIRMED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户侧 TCC 分支屏障。持久化分支状态以保证幂等、空回滚和防悬挂。
CREATE TABLE IF NOT EXISTS tcc_branch (
    branch_id CHAR(26) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    branch_type VARCHAR(32) NOT NULL,
    resource_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'INIT',
    rollback_type VARCHAR(16) NULL,
    barrier_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (branch_id),
    UNIQUE KEY uk_tcc_branch_resource (xid, branch_type, resource_id),
    KEY idx_tcc_branch_transaction (transaction_id),
    KEY idx_tcc_branch_recovery (status, updated_at),
    CONSTRAINT ck_tcc_branch_status CHECK (
        status IN ('INIT', 'TRIED', 'CONFIRMED', 'CANCELLED', 'MANUAL_REVIEW')
    ),
    CONSTRAINT ck_tcc_branch_rollback CHECK (
        rollback_type IS NULL OR rollback_type IN ('NORMAL', 'EMPTY')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心写接口的幂等受理记录，避免重试造成重复余额或额度变更。
CREATE TABLE IF NOT EXISTS idempotency_record (
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
    UNIQUE KEY uk_idempotency_scope (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_recovery (status, updated_at),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心事件消费去重表，防止重复事件再次修改账户或额度事实。
CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_status_updated (status, updated_at),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心资金审计日志。记录操作证据但不保存支付密码、确认令牌等敏感原文。
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    detail_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_target_time (target_type, target_id, occurred_at),
    KEY idx_audit_trace (trace_id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'OPERATOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心可靠事件表。余额或额度事实与对应事件必须在同一本地事务内提交。
CREATE TABLE IF NOT EXISTS outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 银行卡绑定事实。只存 BIN（前 6 位）、尾号（后 4 位）与掩码值，禁止存完整卡号、证件号、手机号明文。
CREATE TABLE IF NOT EXISTS bank_card (
    card_id CHAR(26) NOT NULL COMMENT '银行卡 ID',
    user_id CHAR(26) NOT NULL COMMENT '所属用户 ID',
    account_id CHAR(26) NOT NULL COMMENT '关联的个人账户 ID',
    bank_code VARCHAR(32) NOT NULL COMMENT '银行编码，如 ICBC、CMB',
    bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    card_type VARCHAR(16) NOT NULL COMMENT 'DEBIT 借记卡，CREDIT 信用卡',
    card_bin CHAR(6) NOT NULL COMMENT '卡号前 6 位 BIN',
    card_last4 CHAR(4) NOT NULL COMMENT '卡号后 4 位',
    holder_masked VARCHAR(64) NOT NULL COMMENT '持卡人姓名掩码',
    id_card_masked VARCHAR(32) NOT NULL COMMENT '身份证号掩码',
    phone_masked VARCHAR(16) NOT NULL COMMENT '预留手机号掩码',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认卡',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/UNBOUND',
    unbound_at DATETIME(3) NULL COMMENT '解绑时间',
    registration_id CHAR(26) NULL COMMENT '来源注册记录 ID',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近变更时间',
    PRIMARY KEY (card_id),
    KEY idx_bank_card_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 银行卡注册表：记录用户注册的银行卡（尚未绑定到账户）。
CREATE TABLE IF NOT EXISTS bank_card_registration (
    registration_id CHAR(26) NOT NULL COMMENT '注册记录 ID',
    user_id CHAR(26) NOT NULL COMMENT '注册操作人',
    bank_code VARCHAR(32) NOT NULL COMMENT '银行编码',
    bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    card_type VARCHAR(16) NOT NULL COMMENT 'DEBIT/CREDIT',
    card_number VARCHAR(19) NOT NULL COMMENT '自动生成的完整卡号',
    card_bin CHAR(6) NOT NULL COMMENT 'BIN 前 6 位',
    card_last4 CHAR(4) NOT NULL COMMENT '尾号后 4 位',
    holder_name VARCHAR(32) NOT NULL COMMENT '持卡人姓名明文',
    id_card_hash BINARY(32) NOT NULL COMMENT '身份证号哈希',
    phone_hash BINARY(32) NOT NULL COMMENT '手机号哈希',
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/BOUND',
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (registration_id),
    KEY idx_bcr_user_status (user_id, status),
    KEY idx_bcr_card_number (card_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账户中心账本与信用模块。
-- 负责复式账本、信用应收、账单和对账证据；已过账凭证及分录只能冲正，不能删除或覆盖。
USE ledger_db;

-- 账本科目主档。科目性质与正常余额方向共同决定余额解释，不能把会计借贷等同于用户借款。
CREATE TABLE IF NOT EXISTS ledger_account (
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
    UNIQUE KEY uk_ledger_account_owner_type (
        owner_type, owner_id, account_type, currency
    ),
    UNIQUE KEY uk_ledger_account_code (account_code),
    KEY idx_ledger_account_owner (owner_type, owner_id),
    CONSTRAINT ck_ledger_account_owner_type CHECK (
        owner_type IN ('SYSTEM', 'USER', 'MERCHANT', 'CREDIT_ACCOUNT')
    ),
    CONSTRAINT ck_ledger_account_class CHECK (
        account_class IN ('ASSET', 'LIABILITY', 'EQUITY')
    ),
    CONSTRAINT ck_ledger_account_direction CHECK (normal_direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_account_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_ledger_account_status CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账本凭证。只有全部分录借贷平衡后才能过账，冲正必须新建反向凭证并保留原凭证。
CREATE TABLE IF NOT EXISTS ledger_voucher (
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
    UNIQUE KEY uk_ledger_voucher_transaction_type (
        transaction_id, voucher_type, reversal_no
    ),
    KEY idx_ledger_voucher_original (original_voucher_id),
    KEY idx_ledger_voucher_status_created (status, created_at),
    CONSTRAINT fk_ledger_voucher_original FOREIGN KEY (original_voucher_id)
        REFERENCES ledger_voucher (voucher_id),
    CONSTRAINT ck_ledger_voucher_balance CHECK (
        total_debit_fen = total_credit_fen AND total_debit_fen > 0
    ),
    CONSTRAINT ck_ledger_voucher_status CHECK (
        status IN ('PREPARED', 'POSTED', 'CANCELLED', 'REVERSED')
    ),
    CONSTRAINT ck_ledger_voucher_reversal_reason CHECK (
        reversal_reason IS NULL OR
        reversal_reason IN ('BUSINESS_REFUND', 'RECONCILIATION', 'SYSTEM_CORRECTION')
    ),
    CONSTRAINT ck_ledger_voucher_reversal CHECK (
        (reversal_no = 0 AND original_voucher_id IS NULL AND reversal_reason IS NULL) OR
        (reversal_no > 0 AND original_voucher_id IS NOT NULL AND reversal_reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 不可变账本分录。金额统一使用分，同一凭证的借方合计必须等于贷方合计。
CREATE TABLE IF NOT EXISTS ledger_entry (
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
    UNIQUE KEY uk_ledger_entry_voucher_sequence (voucher_id, sequence_no),
    KEY idx_ledger_entry_account_cursor (ledger_account_id, created_at, entry_id),
    KEY idx_ledger_entry_transaction (transaction_id),
    CONSTRAINT fk_ledger_entry_voucher FOREIGN KEY (voucher_id)
        REFERENCES ledger_voucher (voucher_id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_account (ledger_account_id),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount_fen > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用应收汇总事实。必须满足已用额度等于未出账、已出账与逾期应收的合计口径。
CREATE TABLE IF NOT EXISTS credit_receivable (
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
CREATE TABLE IF NOT EXISTS credit_purchase (
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
CREATE TABLE IF NOT EXISTS credit_bill (
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
CREATE TABLE IF NOT EXISTS credit_bill_item (
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
    CONSTRAINT fk_credit_bill_item_bill FOREIGN KEY (bill_id) REFERENCES credit_bill (bill_id),
    CONSTRAINT fk_credit_bill_item_purchase FOREIGN KEY (purchase_id)
        REFERENCES credit_purchase (purchase_id),
    CONSTRAINT ck_credit_bill_item_amount CHECK (
        amount_fen > 0 AND allocated_paid_fen + reversed_fen <= amount_fen
    ),
    CONSTRAINT ck_credit_bill_item_status CHECK (
        status IN ('ACTIVE', 'REPAID', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用还款事实。绑定还款草稿和统一资金交易，避免重复确认产生多笔还款。
CREATE TABLE IF NOT EXISTS credit_repayment (
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
CREATE TABLE IF NOT EXISTS credit_repayment_allocation (
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
    CONSTRAINT fk_credit_repayment_allocation_repayment FOREIGN KEY (repayment_id)
        REFERENCES credit_repayment (repayment_id),
    CONSTRAINT ck_credit_repayment_allocation_amount CHECK (amount_fen > 0),
    CONSTRAINT ck_credit_repayment_allocation_type CHECK (
        target_type IN ('OVERDUE_BILL', 'BILL', 'UNBILLED_PURCHASE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 还款分配明细。逐笔指向消费及可选账单，父分配金额必须等于其明细合计。
CREATE TABLE IF NOT EXISTS credit_repayment_allocation_detail (
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
    CONSTRAINT fk_credit_repayment_detail_allocation
        FOREIGN KEY (repayment_id, sequence_no)
        REFERENCES credit_repayment_allocation (repayment_id, sequence_no),
    CONSTRAINT fk_credit_repayment_detail_purchase FOREIGN KEY (purchase_id)
        REFERENCES credit_purchase (purchase_id),
    CONSTRAINT fk_credit_repayment_detail_bill FOREIGN KEY (bill_id)
        REFERENCES credit_bill (bill_id),
    CONSTRAINT ck_credit_repayment_detail_amount CHECK (amount_fen > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用批处理运行记录。业务日期唯一约束保证出账或到期任务重复触发时幂等续跑。
CREATE TABLE IF NOT EXISTS credit_job_run (
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

-- 账本与信用侧 TCC 分支屏障。分支持久化后才能处理重试、空回滚和晚到请求。
CREATE TABLE IF NOT EXISTS tcc_branch (
    branch_id CHAR(26) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    transaction_id CHAR(26) NOT NULL,
    branch_type VARCHAR(32) NOT NULL,
    resource_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'INIT',
    rollback_type VARCHAR(16) NULL,
    barrier_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (branch_id),
    UNIQUE KEY uk_tcc_branch_resource (xid, branch_type, resource_id),
    KEY idx_tcc_branch_transaction (transaction_id),
    KEY idx_tcc_branch_recovery (status, updated_at),
    CONSTRAINT ck_tcc_branch_status CHECK (
        status IN ('INIT', 'TRIED', 'CONFIRMED', 'CANCELLED', 'MANUAL_REVIEW')
    ),
    CONSTRAINT ck_tcc_branch_rollback CHECK (
        rollback_type IS NULL OR rollback_type IN ('NORMAL', 'EMPTY')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 证账实对账差异。只保存差异证据并转人工处置，禁止据此直接修改余额或删除原记录。
CREATE TABLE IF NOT EXISTS reconciliation_diff (
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
    UNIQUE KEY uk_reconciliation_diff_fact (biz_date, transaction_id, diff_type),
    KEY idx_reconciliation_diff_status_created (status, created_at),
    CONSTRAINT ck_reconciliation_diff_status CHECK (
        status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 账本与信用可靠事件表。凭证、应收或账单事实与事件必须同事务提交。
CREATE TABLE IF NOT EXISTS outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务中心。
-- 负责编排交易来源、确认、风控与全局 TCC；不直接修改账户余额、额度或账本。
USE business_db;

-- 模拟充值策略版本。策略控制演示虚拟资金来源，不能代表真实充值通道。
CREATE TABLE IF NOT EXISTS recharge_policy (
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
    UNIQUE KEY uk_recharge_policy_code_version (policy_code, version),
    UNIQUE KEY uk_recharge_policy_active_slot (active_slot),
    CONSTRAINT ck_recharge_policy_limits CHECK (
        single_limit_fen > 0 AND daily_limit_fen > 0 AND daily_count_limit > 0
    ),
    CONSTRAINT ck_recharge_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户每日模拟充值额度占用。处理中额度在结果未知时必须保留，避免并发绕过限额。
CREATE TABLE IF NOT EXISTS recharge_daily_usage (
    user_id CHAR(26) NOT NULL,
    business_date DATE NOT NULL,
    processing_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    processing_count INT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 模拟充值来源订单。受理后统一进入资金交易与复式账本，不允许直接增加余额。
CREATE TABLE IF NOT EXISTS recharge_order (
    recharge_order_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    target_account_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    business_date DATE NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'SIMULATED',
    policy_id CHAR(26) NOT NULL,
    policy_version BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    transaction_id CHAR(26) NULL,
    reject_reason_code VARCHAR(32) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (recharge_order_id),
    UNIQUE KEY uk_recharge_order_transaction (transaction_id),
    KEY idx_recharge_order_user_date_status (
        user_id, business_date, status, created_at
    ),
    CONSTRAINT fk_recharge_order_policy FOREIGN KEY (policy_id)
        REFERENCES recharge_policy (policy_id),
    CONSTRAINT ck_recharge_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_recharge_order_channel CHECK (channel = 'SIMULATED'),
    CONSTRAINT ck_recharge_order_status CHECK (
        status IN ('CREATED', 'PROCESSING', 'SUCCESS', 'REJECTED',
                   'CANCELLED', 'MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 受控全额虚拟退款订单。同一原支付最多一笔有效退款，业务退款与对账冲正必须区分。
CREATE TABLE IF NOT EXISTS refund_order (
    refund_order_id CHAR(26) NOT NULL,
    original_transaction_id CHAR(26) NOT NULL,
    merchant_user_id CHAR(26) NOT NULL,
    merchant_account_id CHAR(26) NOT NULL,
    payer_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    original_business_type VARCHAR(16) NOT NULL,
    funding_source VARCHAR(16) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    active_original_key CHAR(26) GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('CREATED', 'PROCESSING', 'SUCCESS', 'MANUAL_REVIEW')
                THEN original_transaction_id
            ELSE NULL
        END
    ) STORED,
    transaction_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (refund_order_id),
    UNIQUE KEY uk_refund_order_transaction (transaction_id),
    UNIQUE KEY uk_refund_order_active_original (active_original_key),
    KEY idx_refund_order_original_time (original_transaction_id, created_at),
    KEY idx_refund_order_merchant_status_time (merchant_account_id, status, created_at),
    CONSTRAINT ck_refund_order_business_type CHECK (
        original_business_type IN ('QR_PAY', 'CREDIT_PAY')
    ),
    CONSTRAINT ck_refund_order_funding_source CHECK (
        funding_source IN ('BALANCE', 'MINI_CREDIT')
    ),
    CONSTRAINT ck_refund_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_refund_order_status CHECK (
        status IN ('CREATED', 'PROCESSING', 'SUCCESS', 'REJECTED',
                   'CANCELLED', 'MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 主动转账草稿。AI 与传统表单共享此结构，但草稿不能替代服务端确认和资金交易事实。
CREATE TABLE IF NOT EXISTS transfer_draft (
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
    KEY idx_transfer_draft_payer_status_time (payer_user_id, status, updated_at),
    KEY idx_transfer_draft_status_expire (status, expires_at),
    CONSTRAINT ck_transfer_draft_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_transfer_draft_users CHECK (payer_user_id <> payee_user_id),
    CONSTRAINT ck_transfer_draft_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_transfer_draft_status CHECK (
        status IN ('DRAFT', 'VALIDATED', 'PENDING_CONFIRMATION', 'SUBMITTED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用还款草稿。固化还款金额与分配摘要，确认令牌必须绑定同一快照。
CREATE TABLE IF NOT EXISTS credit_repayment_draft (
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

-- 动态扫码收款订单。历史 merchant 字段实际指普通用户本人收款账户，不代表独立商户身份。
CREATE TABLE IF NOT EXISTS qr_pay_order (
    qr_order_id CHAR(26) NOT NULL,
    merchant_user_id CHAR(26) NOT NULL,
    merchant_account_id CHAR(26) NOT NULL,
    payer_user_id CHAR(26) NULL,
    transaction_id CHAR(26) NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(128) NULL,
    funding_source VARCHAR(16) NULL,
    refunded_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    scanned_at DATETIME(3) NULL,
    confirmed_at DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (qr_order_id),
    UNIQUE KEY uk_qr_pay_order_transaction (transaction_id),
    KEY idx_qr_pay_order_merchant_status_time (
        merchant_account_id, status, created_at, qr_order_id
    ),
    KEY idx_qr_pay_order_status_expire (status, expires_at),
    CONSTRAINT ck_qr_pay_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_qr_pay_order_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT')
    ),
    CONSTRAINT ck_qr_pay_order_refund_amount CHECK (
        refunded_fen = 0 OR refunded_fen = amount_fen
    ),
    CONSTRAINT ck_qr_pay_order_refund_status CHECK (
        refund_status IN ('NONE', 'PROCESSING', 'SUCCESS', 'MANUAL_REVIEW')
    ),
    CONSTRAINT ck_qr_pay_order_status CHECK (
        status IN ('CREATED', 'SCANNED', 'PENDING_CONFIRMATION', 'RISK_REVIEW',
                   'PROCESSING', 'COMPENSATING', 'MANUAL_REVIEW', 'SUCCESS',
                   'REJECTED', 'CANCELLED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 动态码令牌映射。只保存高熵令牌摘要，原始令牌不得进入日志、存储或分析事件。
CREATE TABLE IF NOT EXISTS qr_pay_token (
    token_digest BINARY(32) NOT NULL,
    qr_order_id CHAR(26) NOT NULL,
    bootstrap_session_hash BINARY(32) NOT NULL,
    h5_session_id CHAR(26) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (token_digest),
    UNIQUE KEY uk_qr_pay_token_order (qr_order_id),
    UNIQUE KEY uk_qr_pay_token_h5_session (h5_session_id),
    KEY idx_qr_pay_token_status_expire (status, expires_at),
    CONSTRAINT fk_qr_pay_token_order FOREIGN KEY (qr_order_id)
        REFERENCES qr_pay_order (qr_order_id),
    CONSTRAINT ck_qr_pay_token_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 长期个人收款码。活动槽位保证每个普通用户最多一个有效码，换码后旧码不能新建订单。
CREATE TABLE IF NOT EXISTS personal_collection_code (
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
    KEY idx_personal_collection_code_owner_time (owner_user_id, created_at),
    KEY idx_personal_collection_code_status_updated (status, updated_at),
    CONSTRAINT ck_personal_collection_code_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 固定金额收款请求。active_order_id 配合 version 条件更新保证并发付款最终最多一笔成功。
CREATE TABLE IF NOT EXISTS collection_request (
    request_id CHAR(26) NOT NULL,
    requester_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    token_digest BINARY(32) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(50) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    active_order_id CHAR(26) NULL,
    transaction_id CHAR(26) NULL,
    cancel_requested_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_collection_request_token (token_digest),
    UNIQUE KEY uk_collection_request_transaction (transaction_id),
    KEY idx_collection_request_status_expire (status, expires_at),
    KEY idx_collection_request_active_order (active_order_id),
    KEY idx_collection_request_owner_time (requester_user_id, created_at),
    CONSTRAINT ck_collection_request_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_collection_request_status CHECK (
        status IN ('OPEN', 'PROCESSING', 'SUCCESS', 'CANCELLED',
                   'EXPIRED', 'MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 单次个人收款付款尝试。个人码允许多笔独立订单，固定请求仅允许抢占成功的订单进入资金处理。
CREATE TABLE IF NOT EXISTS collection_order (
    order_id CHAR(26) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    code_id CHAR(26) NULL,
    request_id CHAR(26) NULL,
    payer_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NOT NULL,
    payee_user_id CHAR(26) NOT NULL,
    payee_account_id CHAR(26) NOT NULL,
    h5_session_id CHAR(26) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(50) NULL,
    funding_source VARCHAR(16) NOT NULL DEFAULT 'BALANCE',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    transaction_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_collection_order_h5_session (h5_session_id),
    UNIQUE KEY uk_collection_order_transaction (transaction_id),
    KEY idx_collection_order_request_status (request_id, status),
    KEY idx_collection_order_code_status (code_id, status),
    KEY idx_collection_order_payer_time (payer_user_id, created_at),
    KEY idx_collection_order_payee_time (payee_user_id, created_at),
    CONSTRAINT fk_collection_order_code FOREIGN KEY (code_id)
        REFERENCES personal_collection_code (code_id),
    CONSTRAINT fk_collection_order_request FOREIGN KEY (request_id)
        REFERENCES collection_request (request_id),
    CONSTRAINT ck_collection_order_source CHECK (
        (mode = 'PERSONAL_QR' AND code_id IS NOT NULL AND request_id IS NULL) OR
        (mode = 'FIXED_REQUEST' AND code_id IS NULL AND request_id IS NOT NULL)
    ),
    CONSTRAINT ck_collection_order_users CHECK (payer_user_id <> payee_user_id),
    CONSTRAINT ck_collection_order_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_collection_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_collection_order_funding_source CHECK (funding_source = 'BALANCE'),
    CONSTRAINT ck_collection_order_status CHECK (
        status IN ('DRAFT', 'PENDING_CONFIRMATION', 'PROCESSING', 'SUCCESS',
                   'FAILED', 'MANUAL_REVIEW', 'CANCELLED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 确认对象活动槽位。每个业务对象同时只能有一个当前有效确认，重新签发会替换旧确认。
CREATE TABLE IF NOT EXISTS confirmation_subject (
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    current_confirmation_id CHAR(26) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (subject_type, subject_id),
    UNIQUE KEY uk_confirmation_subject_current (current_confirmation_id),
    KEY idx_confirmation_subject_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 一次性交易确认。绑定主体、关键资金参数和支付证明，消费后不得再次执行资金动作。
CREATE TABLE IF NOT EXISTS confirmation (
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
        CASE
            WHEN status = 'ACTIVE' THEN CONCAT(subject_type, ':', subject_id)
            ELSE NULL
        END
    ) STORED,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_confirmation_token (token_digest),
    UNIQUE KEY uk_confirmation_payment_proof (payment_proof_id),
    UNIQUE KEY uk_confirmation_active_subject (active_subject_key),
    KEY idx_confirmation_payer_status_version (
        payer_user_id, status, pay_password_version
    ),
    KEY idx_confirmation_status_expire (status, expires_at),
    CONSTRAINT ck_confirmation_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 风控决策证据。保存规则结果及版本，不允许由 AI 输出或前端状态替代确定性风控。
CREATE TABLE IF NOT EXISTS risk_decision (
    decision_id CHAR(26) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NULL,
    rule_version VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (decision_id),
    KEY idx_risk_decision_subject_time (subject_type, subject_id, created_at),
    KEY idx_risk_decision_transaction (transaction_id),
    CONSTRAINT ck_risk_decision_action CHECK (action IN ('PASS', 'REJECT', 'REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 人工处置工单。只推动合法状态流转，运营人员不能通过工单直接修改资金事实。
CREATE TABLE IF NOT EXISTS manual_case (
    case_id CHAR(26) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NULL,
    reason_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('OPEN', 'PROCESSING') THEN CONCAT(subject_type, ':', subject_id)
            ELSE NULL
        END
    ) STORED,
    operator_id CHAR(26) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_manual_case_active_subject (active_subject_key),
    KEY idx_manual_case_status_created (status, created_at),
    KEY idx_manual_case_subject_status (subject_type, subject_id, status),
    CONSTRAINT ck_manual_case_status CHECK (
        status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 统一资金交易事实。source_type 与 source_order_id 唯一，防止更换幂等键导致重复扣款或入账。
-- 已受理交易必须收敛到成功、撤销、冲正或人工处理，不能用含义不明的 FAILED 终止恢复。
CREATE TABLE IF NOT EXISTS fund_transaction (
    transaction_id CHAR(26) NOT NULL,
    business_type VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_order_id CHAR(26) NOT NULL,
    initiator_user_id CHAR(26) NOT NULL,
    payer_account_id CHAR(26) NULL,
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
    UNIQUE KEY uk_fund_transaction_source (source_type, source_order_id),
    UNIQUE KEY uk_fund_transaction_idempotency (
        initiator_user_id, business_type, idempotency_key
    ),
    KEY idx_fund_transaction_recovery (status, updated_at),
    KEY idx_fund_transaction_payee_time (payee_account_id, created_at),
    KEY idx_fund_transaction_business_time (business_type, created_at),
    KEY idx_fund_transaction_related (related_transaction_id),
    CONSTRAINT fk_fund_transaction_related FOREIGN KEY (related_transaction_id)
        REFERENCES fund_transaction (transaction_id),
    CONSTRAINT ck_fund_transaction_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_fund_transaction_business_type CHECK (
        business_type IN ('TRANSFER', 'QR_PAY', 'CREDIT_PAY', 'CREDIT_REPAY',
                          'RECHARGE', 'REFUND')
    ),
    CONSTRAINT ck_fund_transaction_source_type CHECK (
        source_type IN ('TRANSFER_DRAFT', 'QR_PAY_ORDER', 'PERSONAL_QR_ORDER',
                        'COLLECTION_REQUEST_ORDER', 'CREDIT_REPAYMENT_DRAFT',
                        'RECHARGE_ORDER', 'REFUND_ORDER')
    ),
    CONSTRAINT ck_fund_transaction_business_source CHECK (
        (business_type = 'TRANSFER' AND
            source_type IN ('TRANSFER_DRAFT', 'PERSONAL_QR_ORDER',
                            'COLLECTION_REQUEST_ORDER')) OR
        (business_type IN ('QR_PAY', 'CREDIT_PAY') AND source_type = 'QR_PAY_ORDER') OR
        (business_type = 'CREDIT_REPAY' AND source_type = 'CREDIT_REPAYMENT_DRAFT') OR
        (business_type = 'RECHARGE' AND source_type = 'RECHARGE_ORDER') OR
        (business_type = 'REFUND' AND source_type = 'REFUND_ORDER')
    ),
    CONSTRAINT ck_fund_transaction_funding_source CHECK (
        funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    ),
    CONSTRAINT ck_fund_transaction_business_funding CHECK (
        (business_type IN ('TRANSFER', 'QR_PAY', 'CREDIT_REPAY') AND
            funding_source = 'BALANCE') OR
        (business_type = 'CREDIT_PAY' AND funding_source = 'MINI_CREDIT') OR
        (business_type = 'RECHARGE' AND funding_source = 'SYSTEM_ISSUANCE') OR
        (business_type = 'REFUND' AND funding_source IN ('BALANCE', 'MINI_CREDIT'))
    ),
    CONSTRAINT ck_fund_transaction_accounts CHECK (
        (business_type = 'RECHARGE' AND payer_account_id IS NULL AND
            funding_source = 'SYSTEM_ISSUANCE') OR
        (business_type <> 'RECHARGE' AND payer_account_id IS NOT NULL AND
            payer_account_id <> payee_account_id AND
            funding_source <> 'SYSTEM_ISSUANCE')
    ),
    CONSTRAINT ck_fund_transaction_related CHECK (
        (business_type = 'REFUND' AND related_transaction_id IS NOT NULL) OR
        (business_type <> 'REFUND' AND related_transaction_id IS NULL)
    ),
    CONSTRAINT ck_fund_transaction_status CHECK (
        status IN ('PROCESSING', 'COMPENSATING', 'MANUAL_REVIEW',
                   'SUCCESS', 'REVERSED', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 全局 TCC 协调事实。记录恢复游标和重试状态，只有全部资金及账本分支一致后才能发布成功。
CREATE TABLE IF NOT EXISTS tcc_global (
    transaction_id CHAR(26) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transaction_id),
    UNIQUE KEY uk_tcc_global_xid (xid),
    KEY idx_tcc_global_recovery (status, next_retry_at),
    CONSTRAINT fk_tcc_global_transaction FOREIGN KEY (transaction_id)
        REFERENCES fund_transaction (transaction_id),
    CONSTRAINT ck_tcc_global_status CHECK (
        status IN ('PROCESSING', 'COMMITTING', 'ROLLING_BACK',
                   'SUCCESS', 'CANCELLED', 'MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务中心写接口幂等记录。同键异参必须冲突，同键同参必须返回原业务结果。
CREATE TABLE IF NOT EXISTS idempotency_record (
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
    UNIQUE KEY uk_idempotency_scope (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_recovery (status, updated_at),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务中心事件消费去重表，避免重复事件再次驱动交易投影或状态流转。
CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_status_updated (status, updated_at),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务中心审计日志。记录交易编排证据，但不得保存支付密码、确认令牌和二维码原始令牌。
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    detail_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_target_time (target_type, target_id, occurred_at),
    KEY idx_audit_trace (trace_id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'OPERATOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务中心可靠事件表。来源订单、交易终态与事件必须在同一本地事务内一致提交。
CREATE TABLE IF NOT EXISTS outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 服务。
-- 只保存脱敏会话、偏好和工具调用证据；AI 不持有账户写权限，也不能决定资金终态。
USE agent_db;

-- AI 对话会话。结构化槽位仅用于草稿编排，不能替代业务库中的金额、账户或交易事实。
CREATE TABLE IF NOT EXISTS agent_session (
    session_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    summary TEXT NULL,
    title VARCHAR(100) NULL COMMENT '用户自定义会话标题',
    slots_json JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_active_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (session_id),
    KEY idx_agent_session_user_active (user_id, last_active_at),
    KEY idx_agent_session_status_active (status, last_active_at),
    CONSTRAINT ck_agent_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 脱敏对话消息。client_message_id 与角色联合唯一，防止重试重复生成同一回复。
CREATE TABLE IF NOT EXISTS agent_message (
    message_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content_redacted TEXT NOT NULL,
    token_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_agent_message_client_role (session_id, client_message_id, role),
    KEY idx_agent_message_session_time (session_id, created_at),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id)
        REFERENCES agent_session (session_id),
    CONSTRAINT ck_agent_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MCP 工具调用证据。只保存规范化请求摘要和标准结果，不落原始敏感参数。
CREATE TABLE IF NOT EXISTS tool_call_log (
    tool_call_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    duration_ms INT UNSIGNED NOT NULL,
    trace_id CHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (tool_call_id),
    KEY idx_tool_call_trace_time (trace_id, occurred_at),
    KEY idx_tool_call_session_time (session_id, occurred_at),
    CONSTRAINT fk_tool_call_session FOREIGN KEY (session_id)
        REFERENCES agent_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户授权的低敏偏好。偏好只能影响候选排序，不能自动选定收款人、金额或跳过确认。
CREATE TABLE IF NOT EXISTS preference (
    preference_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    preference_type VARCHAR(32) NOT NULL,
    value_encrypted VARBINARY(1024) NOT NULL,
    consent_version VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (preference_id),
    UNIQUE KEY uk_preference_user_type (user_id, preference_type),
    KEY idx_preference_user_status (user_id, status),
    CONSTRAINT ck_preference_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 服务写接口幂等记录，避免重复消息或工具编排造成额外业务副作用。
CREATE TABLE IF NOT EXISTS idempotency_record (
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
    UNIQUE KEY uk_idempotency_scope (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_recovery (status, updated_at),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 服务审计日志。detail_json 必须脱敏，禁止记录密码、令牌、完整账号或未脱敏对话。
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    detail_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_target_time (target_type, target_id, occurred_at),
    KEY idx_audit_trace (trace_id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'OPERATOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 服务可靠事件表。会话事实与事件同事务写入，事件载荷不得包含原始敏感信息。
CREATE TABLE IF NOT EXISTS outbox_event (
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
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 监控与分析投影。
-- 仅消费可靠事件形成可重建投影，不作为余额、额度、账本或交易终态的事实来源。
USE metrics_db;

-- 监控消费者去重表，确保重复投递不会让实时或离线指标重复计数。
CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_status_updated (status, updated_at),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 不符合事件契约的数据隔离区。问题未解决前不得进入指标计算或报表发布。
CREATE TABLE IF NOT EXISTS quarantined_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    schema_version SMALLINT UNSIGNED NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    quarantined_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3) NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_quarantined_event_status_time (status, quarantined_at),
    CONSTRAINT ck_quarantined_event_status CHECK (
        status IN ('OPEN', 'REPROCESSED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 脱敏分析事件明细。用于重建统计投影，不得被资金判断或用户交易状态查询使用。
CREATE TABLE IF NOT EXISTS analytics_event (
    event_id CHAR(26) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version SMALLINT UNSIGNED NOT NULL,
    business_type VARCHAR(16) NULL,
    source_type VARCHAR(32) NULL,
    source_order_id CHAR(26) NULL,
    funding_source VARCHAR(16) NULL,
    transaction_id CHAR(26) NULL,
    original_transaction_id CHAR(26) NULL,
    account_id CHAR(26) NULL,
    merchant_account_id CHAR(26) NULL,
    account_id_hash BINARY(32) NULL,
    merchant_account_id_hash BINARY(32) NULL,
    direction VARCHAR(16) NULL,
    stat_category VARCHAR(32) NULL,
    amount_fen BIGINT UNSIGNED NULL,
    occurred_at DATETIME(3) NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    dimensions_json JSON NULL,
    metrics_json JSON NULL,
    trace_id CHAR(32) NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_analytics_event_account_time (account_id, occurred_at),
    KEY idx_analytics_event_merchant_time (merchant_account_id, occurred_at),
    KEY idx_analytics_event_business_time (business_type, occurred_at),
    CONSTRAINT ck_analytics_event_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    ),
    CONSTRAINT ck_analytics_event_direction CHECK (
        direction IS NULL OR direction IN ('INCOME', 'EXPENSE', 'NEUTRAL')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 普通用户日收支投影。按事件 ID 去重，仅统计确定终态并区分转账、消费、还款和充值。
CREATE TABLE IF NOT EXISTS personal_cashflow_daily (
    account_id CHAR(26) NOT NULL,
    stat_date DATE NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    transfer_income_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    transfer_expense_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    balance_payment_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    credit_consumption_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    credit_repayment_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    recharge_inflow_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, stat_date, definition_version),
    KEY idx_personal_cashflow_date_quality (stat_date, quality_status),
    CONSTRAINT ck_personal_cashflow_quality CHECK (
        quality_status IN ('PENDING', 'PASSED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 普通用户交易对象统计。仅用于本人分析展示，不表示好友关系或授权关系。
CREATE TABLE IF NOT EXISTS personal_counterparty_stat (
    account_id CHAR(26) NOT NULL,
    counterparty_account_id CHAR(26) NOT NULL,
    period_type VARCHAR(8) NOT NULL,
    period_start DATE NOT NULL,
    income_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expense_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_success_at DATETIME(3) NULL,
    definition_version INT UNSIGNED NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (
        account_id, counterparty_account_id, period_type,
        period_start, definition_version
    ),
    KEY idx_personal_counterparty_period (account_id, period_type, period_start),
    CONSTRAINT ck_personal_counterparty_period_type CHECK (period_type IN ('DAY', 'MONTH')),
    CONSTRAINT ck_personal_counterparty_accounts CHECK (account_id <> counterparty_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 普通用户扫码收款日投影。表名沿用历史 merchant 命名，不代表独立商户身份或 B 端权限。
CREATE TABLE IF NOT EXISTS merchant_business_daily (
    merchant_account_id CHAR(26) NOT NULL,
    stat_date DATE NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    success_order_count INT UNSIGNED NOT NULL DEFAULT 0,
    failed_order_count INT UNSIGNED NOT NULL DEFAULT 0,
    processing_order_count INT UNSIGNED NOT NULL DEFAULT 0,
    success_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    balance_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    credit_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    net_receipt_fen BIGINT NOT NULL DEFAULT 0,
    quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (merchant_account_id, stat_date, definition_version),
    KEY idx_merchant_business_date_quality (stat_date, quality_status),
    CONSTRAINT ck_merchant_business_receipt CHECK (
        success_receipt_fen = balance_receipt_fen + credit_receipt_fen
    ),
    CONSTRAINT ck_merchant_business_net CHECK (
        net_receipt_fen =
            CAST(success_receipt_fen AS SIGNED) - CAST(refund_fen AS SIGNED)
    ),
    CONSTRAINT ck_merchant_business_quality CHECK (
        quality_status IN ('PENDING', 'PASSED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 普通用户扫码收款日对账投影。差异非零时不得标记 MATCHED，必须保留差异证据。
CREATE TABLE IF NOT EXISTS merchant_reconciliation_daily (
    merchant_account_id CHAR(26) NOT NULL,
    biz_date DATE NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    successful_order_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    successful_refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expected_net_fen BIGINT NOT NULL,
    ledger_net_fen BIGINT NOT NULL,
    diff_fen BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_diff_id CHAR(26) NULL,
    checked_at DATETIME(3) NOT NULL,
    PRIMARY KEY (merchant_account_id, biz_date, definition_version),
    KEY idx_merchant_reconciliation_status_date (status, biz_date),
    CONSTRAINT ck_merchant_reconciliation_expected CHECK (
        expected_net_fen =
            CAST(successful_order_fen AS SIGNED) - CAST(successful_refund_fen AS SIGNED)
    ),
    CONSTRAINT ck_merchant_reconciliation_diff CHECK (
        diff_fen = ledger_net_fen - expected_net_fen
    ),
    CONSTRAINT ck_merchant_reconciliation_status CHECK (
        status IN ('PENDING', 'MATCHED', 'DIFF', 'RESOLVED')
    ),
    CONSTRAINT ck_merchant_reconciliation_match CHECK (
        status <> 'MATCHED' OR diff_fen = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 指标口径版本。公式和维度变更必须新增版本，禁止静默覆盖历史报表定义。
CREATE TABLE IF NOT EXISTS metric_definition (
    metric_code VARCHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    formula TEXT NOT NULL,
    dimensions_json JSON NOT NULL,
    owner_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_at DATETIME(3) NOT NULL,
    PRIMARY KEY (metric_code, version),
    KEY idx_metric_definition_status_effective (status, effective_at),
    CONSTRAINT ck_metric_definition_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 分钟级实时指标投影。质量未通过的数据不得作为可信看板结果发布。
CREATE TABLE IF NOT EXISTS minute_metric (
    metric_code VARCHAR(64) NOT NULL,
    bucket_at DATETIME(3) NOT NULL,
    dimension_hash BINARY(32) NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    dimensions_json JSON NOT NULL,
    value_decimal DECIMAL(24,6) NOT NULL,
    quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (metric_code, bucket_at, dimension_hash, definition_version),
    KEY idx_minute_metric_bucket_quality (bucket_at, quality_status),
    CONSTRAINT fk_minute_metric_definition FOREIGN KEY (metric_code, definition_version)
        REFERENCES metric_definition (metric_code, version),
    CONSTRAINT ck_minute_metric_quality CHECK (
        quality_status IN ('PENDING', 'PASSED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- T+1 日指标投影。绑定口径版本并通过质量门禁后才能发布；列定义与 V202608051211 一致。
CREATE TABLE IF NOT EXISTS daily_metric (
    metric_date DATE NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    dimension_hash BINARY(32) NOT NULL,
    dimensions_json JSON NOT NULL,
    value_decimal DECIMAL(24,6) NOT NULL,
    quality_status VARCHAR(16) NOT NULL,
    version INT UNSIGNED NOT NULL,
    PRIMARY KEY (metric_date, metric_code, dimension_hash, version),
    KEY idx_daily_metric_code (metric_code, metric_date),
    CONSTRAINT ck_daily_metric_quality CHECK (
        quality_status IN ('PENDING', 'PASSED', 'FAILED', 'UNKNOWN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 数据质量检查结果。保存期望、实际与证据，失败结果用于阻断不可信报表发布。
CREATE TABLE IF NOT EXISTS quality_result (
    result_id CHAR(26) NOT NULL,
    task_code VARCHAR(64) NOT NULL,
    data_date DATE NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expected_value DECIMAL(24,6) NULL,
    actual_value DECIMAL(24,6) NULL,
    evidence_json JSON NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    PRIMARY KEY (result_id),
    UNIQUE KEY uk_quality_result_task_date_rule (task_code, data_date, rule_code),
    KEY idx_quality_result_status_checked (status, checked_at),
    CONSTRAINT ck_quality_result_status CHECK (status IN ('PASSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 监控告警闭环。状态记录确认、解决与关闭过程，不提供任何直接改资金入口；列定义与 V202608051211 一致。
CREATE TABLE IF NOT EXISTS monitor_alert (
    alert_id CHAR(26) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    subject_id VARCHAR(128) NULL,
    evidence_json JSON NOT NULL,
    assignee_id CHAR(26) NULL,
    last_reason VARCHAR(256) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    opened_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    PRIMARY KEY (alert_id),
    KEY idx_monitor_alert_status (status, severity, opened_at),
    CONSTRAINT ck_monitor_alert_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CLOSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

FLUSH PRIVILEGES;
