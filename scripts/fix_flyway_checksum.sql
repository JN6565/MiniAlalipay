-- 修复合并导致的 Flyway checksum 不匹配
-- 直接用当前本地文件的 CRC32 checksum 更新 flyway_schema_history 表
-- 执行一次后可删除此脚本

-- V202608071800 本地 checksum: -1816356998
UPDATE business_db.flyway_schema_history
SET checksum = -1816356998
WHERE version = '202608071800';

-- V202608072100 本地 checksum: 1760750326
UPDATE business_db.flyway_schema_history
SET checksum = 1760750326
WHERE version = '202608072100';

-- V202608072130 本地 checksum: -867009617
UPDATE business_db.flyway_schema_history
SET checksum = -867009617
WHERE version = '202608072130';
