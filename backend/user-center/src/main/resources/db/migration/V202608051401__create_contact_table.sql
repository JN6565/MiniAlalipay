-- 联系人表。
-- 保存由成功转账自动形成的单向常用收款人投影，不表示好友或通讯录关系。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 5.4 节。

CREATE TABLE IF NOT EXISTS user_db.contact (
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
    KEY idx_contact_owner_pinned (owner_user_id, pinned, hidden, last_success_at),
    KEY idx_contact_payee (payee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
