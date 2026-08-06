-- 观察者角色已取消：先清理历史授权，再重建角色约束，避免旧会话或残留数据继续获得 B 端权限。
DELETE FROM user_db.role_assignment
WHERE role_code = 'OBSERVER';

-- 仅删除固定的历史观察者演示用户，避免影响不存在该角色的普通业务用户。
DELETE FROM user_db.credential
WHERE user_id = '01J00000000000000000000002';

DELETE FROM user_db.app_user
WHERE user_id = '01J00000000000000000000002';

-- MySQL 8.0 的 DROP CHECK 不支持 IF EXISTS，约束必然存在，直接删除后重建为不含 OBSERVER 的角色白名单。
ALTER TABLE user_db.role_assignment
    DROP CHECK ck_role_assignment_role,
    ADD CONSTRAINT ck_role_assignment_role
        CHECK (role_code IN ('USER', 'MERCHANT', 'OPERATOR', 'ADMIN'));
