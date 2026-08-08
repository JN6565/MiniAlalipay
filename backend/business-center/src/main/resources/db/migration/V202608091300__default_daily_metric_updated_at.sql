-- 对齐共享库 daily_metric 的 updated_at 列，使指标发布语句可正常写入（前向迁移）。
--
-- 背景：部署初始化脚本先于 Flyway 以 CREATE TABLE IF NOT EXISTS 建表，使 V202608051211 的
-- daily_metric 定义成为空操作，真实表额外保留了 updated_at DATETIME(3) NOT NULL 且无默认值；
-- 发布指标时按预期 schema 省略该列，MySQL 严格模式拒绝写入。
--
-- 目标 schema 无 updated_at 列；对漂移库仅补默认值（保留该列，避免影响其他读取方），对已对齐库为空操作。
SET @daily_updated_at_no_default = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='metrics_db' AND TABLE_NAME='daily_metric' AND COLUMN_NAME='updated_at'
      AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL
);
-- MySQL 的 ALTER COLUMN ... SET DEFAULT 不接受表达式，需用 MODIFY COLUMN 补 CURRENT_TIMESTAMP 默认值。
SET @sql = IF(@daily_updated_at_no_default > 0,
    'ALTER TABLE metrics_db.daily_metric MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
