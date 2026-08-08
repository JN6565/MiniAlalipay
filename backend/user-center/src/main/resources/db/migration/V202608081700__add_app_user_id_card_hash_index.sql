-- =====================================================================
-- 为 app_user.id_card_hash 创建普通索引
-- 设计目的：身份绑定按身份证号哈希做全系统查重（existsByIdCardHashExcluding），
-- 查重路径需要索引支撑，否则随用户量增长会退化为全表扫描。
-- 不使用唯一索引：唯一性由应用层查重 + 乐观锁更新保障，避免既有重复数据导致迁移失败。
-- 幂等处理：索引已存在时跳过创建，保证迁移可重复执行。
-- =====================================================================
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'user_db'
      AND TABLE_NAME = 'app_user'
      AND INDEX_NAME = 'idx_app_user_id_card_hash'
);
SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_app_user_id_card_hash ON user_db.app_user (id_card_hash)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
