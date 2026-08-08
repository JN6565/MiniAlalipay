-- 补建 Dashboard 实时指标扫描索引。
-- 背景：运营查询索引命名统一迁移（V202608071800）的重建版未包含 metrics_db 侧索引；
-- Dashboard 实时指标在 metricCode 为空时按 occurred_at 范围扫描全量事件，
-- 既有 (event_type,occurred_at) 索引无法服务无等值条件的范围扫描，
-- 补单列索引避免全表扫描。独立版本号，不与任何分支的同版本迁移冲突。
-- 当前分支的 V202608071800 已创建同名索引；这里保留独立迁移以兼容未包含该索引的历史分支。
-- MySQL 不支持 CREATE INDEX IF NOT EXISTS，因此查询元数据后再动态执行，防止重复 DDL 阻断服务启动。
SET @analytics_event_occurred_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = 'metrics_db'
      AND table_name = 'analytics_event'
      AND index_name = 'idx_analytics_event_occurred'
);
SET @analytics_event_occurred_index_sql = IF(
    @analytics_event_occurred_index_exists = 0,
    'CREATE INDEX idx_analytics_event_occurred ON metrics_db.analytics_event (occurred_at)',
    'SELECT 1'
);
PREPARE analytics_event_occurred_index_statement FROM @analytics_event_occurred_index_sql;
EXECUTE analytics_event_occurred_index_statement;
DEALLOCATE PREPARE analytics_event_occurred_index_statement;
