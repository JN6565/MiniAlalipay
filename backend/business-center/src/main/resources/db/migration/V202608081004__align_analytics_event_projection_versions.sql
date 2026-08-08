-- 对齐 Flyway 早期建表与部署初始化脚本：监控分析事件必须记录事件 Schema 版本和指标口径版本。
-- 两个字段均为非空事实；投影代码在同一次变更中显式写入，禁止依赖数据库隐式默认值。
SET @analytics_event_version_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = 'metrics_db'
      AND table_name = 'analytics_event'
      AND column_name = 'event_version'
);

SET @analytics_event_version_sql = IF(
    @analytics_event_version_exists = 0,
    'ALTER TABLE metrics_db.analytics_event ADD COLUMN event_version SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER event_type',
    'SELECT 1'
);
PREPARE analytics_event_version_statement FROM @analytics_event_version_sql;
EXECUTE analytics_event_version_statement;
DEALLOCATE PREPARE analytics_event_version_statement;

SET @analytics_definition_version_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = 'metrics_db'
      AND table_name = 'analytics_event'
      AND column_name = 'definition_version'
);

SET @analytics_definition_version_sql = IF(
    @analytics_definition_version_exists = 0,
    'ALTER TABLE metrics_db.analytics_event ADD COLUMN definition_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER occurred_at',
    'SELECT 1'
);
PREPARE analytics_definition_version_statement FROM @analytics_definition_version_sql;
EXECUTE analytics_definition_version_statement;
DEALLOCATE PREPARE analytics_definition_version_statement;
