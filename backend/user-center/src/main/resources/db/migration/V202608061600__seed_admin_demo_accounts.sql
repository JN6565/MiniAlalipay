-- B 端演示账号初始化：仅创建后台身份、登录凭证和最小 RBAC 角色，不创建资金账户、余额或支付密码。
-- 登录密码仅以 BCrypt 哈希入库；明文只通过受控演示凭据分发，不得写入迁移、日志或前端存储。

INSERT INTO user_db.app_user (
    user_id,
    registration_id,
    account_number,
    phone_number,
    real_name,
    nickname,
    phone_tail,
    identity_status,
    status,
    version,
    created_at,
    updated_at
) VALUES
    ('01J00000000000000000000001', '01R00000000000000000000001', '6200000000000001', '13900000001', '演示运营人员', '运营演示账号', '0001', 'VERIFIED', 'ACTIVE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('01J00000000000000000000002', '01R00000000000000000000002', '6200000000000002', '13900000002', '演示观察者', '观察者演示账号', '0002', 'VERIFIED', 'ACTIVE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('01J00000000000000000000003', '01R00000000000000000000003', '6200000000000003', '13900000003', '演示系统管理员', '管理员演示账号', '0003', 'VERIFIED', 'ACTIVE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO user_db.credential (
    user_id,
    login_password_hash,
    payment_password_hash,
    login_fail_count,
    pay_fail_count,
    login_lock_until,
    pay_lock_until,
    pay_password_version,
    version,
    updated_at
) VALUES
    ('01J00000000000000000000001', '$2b$12$Q2dFnbStcvDVcQwyESC7EubeokRR25HyQ2MWLw8QnvO5Yy4wywN6O', NULL, 0, 0, NULL, NULL, 0, 0, UTC_TIMESTAMP(3)),
    ('01J00000000000000000000002', '$2b$12$CziJtDE0o9t8bbrY8nnb9utU4bztTLnjz1BqsAs9RRL.n.VXwsBf.', NULL, 0, 0, NULL, NULL, 0, 0, UTC_TIMESTAMP(3)),
    ('01J00000000000000000000003', '$2b$12$2EPrEZHsHr4haLcsJApS4OevQ5sgvGo1xDXYrY49e8EyxVPX47NjC', NULL, 0, 0, NULL, NULL, 0, 0, UTC_TIMESTAMP(3));

-- 每个演示账号仅具备一项后台职责，避免多角色并集掩盖页面及接口授权问题。
INSERT INTO user_db.role_assignment (user_id, role_code, created_at) VALUES
    ('01J00000000000000000000001', 'OPERATOR', UTC_TIMESTAMP(3)),
    ('01J00000000000000000000002', 'OBSERVER', UTC_TIMESTAMP(3)),
    ('01J00000000000000000000003', 'ADMIN', UTC_TIMESTAMP(3));
