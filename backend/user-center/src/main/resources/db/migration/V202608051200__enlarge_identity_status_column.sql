-- 扩大 identity_status 列宽：VARCHAR(16) 无法容纳 'PENDING_VERIFICATION'（20 字符）。

ALTER TABLE user_db.app_user
    MODIFY COLUMN identity_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION';
