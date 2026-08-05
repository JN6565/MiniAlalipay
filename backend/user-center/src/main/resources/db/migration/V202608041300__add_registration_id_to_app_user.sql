-- 为 app_user 表添加 registration_id 列。
-- registration_id 是用户中心生成的注册幂等键，用于开户恢复和既有资源查询。

ALTER TABLE user_db.app_user
ADD COLUMN registration_id CHAR(26) NOT NULL AFTER user_id;

-- 添加唯一索引
ALTER TABLE user_db.app_user
ADD UNIQUE KEY uk_app_user_registration (registration_id);
