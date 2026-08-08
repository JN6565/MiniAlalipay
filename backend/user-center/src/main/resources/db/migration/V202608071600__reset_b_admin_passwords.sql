-- 重置 B 端演示账号的登录密码为统一初始密码（已执行过的迁移，重建版本）。
-- 使用 ON DUPLICATE KEY 保证幂等可重放。

INSERT INTO user_db.credential (user_id, login_password_hash, version, updated_at)
VALUES
    ('01J00000000000000000000001', '$2b$12$Q2dFnbStcvDVcQwyESC7EubeokRR25HyQ2MWLw8QnvO5Yy4wywN6O', 0, UTC_TIMESTAMP(3)),
    ('01J00000000000000000000003', '$2b$12$2EPrEZHsHr4haLcsJApS4OevQ5sgvGo1xDXYrY49e8EyxVPX47NjC', 0, UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE login_password_hash = VALUES(login_password_hash), updated_at = VALUES(updated_at);
