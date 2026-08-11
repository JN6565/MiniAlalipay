-- 联调临时：重置两个已实名 C 端用户的登录密码为 Demo@12345（BCrypt）
UPDATE user_db.credential
SET login_password_hash = '$2a$10$RbJOrdd5gFJp4xXMDl5dhewyieWYbTrtcEqNpWrNf5IBWQg/2yiDm',
    login_fail_count = 0,
    login_lock_until = NULL,
    updated_at = UTC_TIMESTAMP(3)
WHERE user_id IN ('31A4CE1EC03C48EEBA3F063B00', '315B5372D45A49A7A42C963EC8');
SELECT user_id, LENGTH(login_password_hash) AS pwdlen FROM user_db.credential WHERE user_id IN ('31A4CE1EC03C48EEBA3F063B00', '315B5372D45A49A7A42C963EC8');
