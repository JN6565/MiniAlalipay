-- 用户认证模型升级为“手机号或系统账户号登录”。
-- 完整手机号仅由用户中心持有，唯一索引是并发注册下禁止重复手机号的最终防线。
ALTER TABLE user_db.app_user
    ADD COLUMN phone_number VARCHAR(11) NULL AFTER login_name,
    ADD COLUMN real_name VARCHAR(64) NULL AFTER phone_number,
    ADD UNIQUE KEY uk_app_user_phone_number (phone_number),
    ADD KEY idx_app_user_real_name_status (real_name, status);

-- 历史演示用户没有完整手机号和真实姓名，保留 NULL 以避免伪造身份事实；
-- 新版注册接口和持久化代码会保证新记录两个字段均非空。
