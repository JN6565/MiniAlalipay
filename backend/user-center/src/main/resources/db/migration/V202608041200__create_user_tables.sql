-- 用户中心表结构迁移。
-- 表定义参考 docs/minialalipay/minialalipay-database-design.md 第 5 章。
-- 使用 CREATE TABLE IF NOT EXISTS 保证与 Docker 初始化脚本共存时不冲突。

-- ============================================================
-- user_db 中的用户表
-- ============================================================

-- 用户主体表。保存用户展示资料和账户级状态，不保存密码或 RBAC 角色。
CREATE TABLE IF NOT EXISTS user_db.app_user (
    user_id CHAR(26) NOT NULL,
    registration_id CHAR(26) NOT NULL,
    login_name VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    phone_tail CHAR(4) NULL,
    identity_status VARCHAR(16) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    status VARCHAR(16) NOT NULL DEFAULT 'PROVISIONING',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_registration (registration_id),
    UNIQUE KEY uk_app_user_login_name (login_name),
    KEY idx_app_user_nickname_status (nickname, status),
    KEY idx_app_user_status_created (status, created_at),
    CONSTRAINT ck_app_user_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_identity CHECK (identity_status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 凭证表。保存登录密码、支付密码的强哈希以及两套独立的失败锁定状态。
CREATE TABLE IF NOT EXISTS user_db.credential (
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
    KEY idx_credential_pay_lock (pay_lock_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 角色分配表。保存 RBAC 角色分配，是系统角色授权的唯一事实来源。
CREATE TABLE IF NOT EXISTS user_db.role_assignment (
    user_id CHAR(26) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    KEY idx_role_assignment_role (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
