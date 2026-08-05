-- 支付密码证明表。
-- 保存支付密码验证成功后签发的短期一次性证明，业务库只保存其逻辑引用。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 5.3 节。

CREATE TABLE IF NOT EXISTS user_db.payment_proof (
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
    KEY idx_payment_proof_user_status (user_id, status, expires_at),
    CONSTRAINT ck_payment_proof_status CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
