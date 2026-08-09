-- 重置 B 端演示账号登录密码为项目可校验的 BCrypt 强哈希。
--
-- 背景：01J00000000000000000000011（系统管理员）与 01J00000000000000000000012（运营）
-- 的 login_password_hash 为非 BCrypt 格式，BCryptPasswordEncoder 校验时直接抛异常，
-- 导致 B 端登录必然失败。此处按受控演示凭据分发重置为成本 12 的 BCrypt 哈希；
-- 明文只通过受控演示凭据分发，不得写入迁移、日志或前端存储。
-- 同时清零失败次数与登录锁定，避免历史尝试触发的 10 分钟锁定残留。
--
-- 注：该 SQL 原为 V202608071600 内容；因与 master 分支同一版本号冲突，
-- 合并后保留 master 已执行的 INSERT 版，本 UPDATE 提升为独立新版本以保证可重放。

UPDATE user_db.credential
SET login_password_hash = '$2b$12$DDDx5y57mQ8CVwlhHLS1juPwkAS4EiiSyhTEMrvll4I/eXq8AulMa',
    login_fail_count = 0,
    login_lock_until = NULL,
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE user_id = '01J00000000000000000000011';

UPDATE user_db.credential
SET login_password_hash = '$2b$12$0tfLxhGlZlRpfMNZavSJlOG4Dpa8RuBSyjvLqbzX232kKLkoi.F5i',
    login_fail_count = 0,
    login_lock_until = NULL,
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE user_id = '01J00000000000000000000012';
