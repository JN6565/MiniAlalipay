-- 幂等记录表。
-- 保存请求幂等受理、资源绑定和响应快照，防止重复提交。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 10.2 节。

CREATE TABLE IF NOT EXISTS user_db.idempotency_record (
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
    UNIQUE KEY uk_idempotency_principal (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_status (status, updated_at),
    KEY idx_idempotency_expires (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
