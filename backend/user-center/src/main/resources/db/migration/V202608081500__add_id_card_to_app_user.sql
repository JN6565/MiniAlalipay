-- 为 app_user 表新增身份证号相关字段，用于绑卡时三要素交叉比对。
-- id_card 保存掩码格式（如 3301**********1234），仅供展示；
-- id_card_hash 保存明文 SHA-256 哈希，用于 account-center 跨服务校验时比对。
-- 所有语句通过 information_schema 条件判断，对"已添加"与"未添加"两种库均幂等可重放。

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='user_db' AND TABLE_NAME='app_user' AND COLUMN_NAME='id_card') = 0,
    'ALTER TABLE user_db.app_user ADD COLUMN id_card VARCHAR(32) NULL COMMENT ''身份证号掩码，绑定身份后保存，如 3301**********1234''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='user_db' AND TABLE_NAME='app_user' AND COLUMN_NAME='id_card_hash') = 0,
    'ALTER TABLE user_db.app_user ADD COLUMN id_card_hash BINARY(32) NULL COMMENT ''身份证号明文哈希，用于绑卡时三要素交叉比对''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
