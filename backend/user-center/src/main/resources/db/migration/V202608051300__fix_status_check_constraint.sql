-- 修复 status 列的 CHECK 约束：Docker 初始化脚本定义为 (ACTIVE, LOCKED, CLOSED)，
-- 但代码需要插入 PROVISIONING 状态。需要先删除旧约束再添加新约束。

ALTER TABLE user_db.app_user
    DROP CHECK ck_app_user_status;

ALTER TABLE user_db.app_user
    ADD CONSTRAINT ck_app_user_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'DISABLED'));
