-- 对齐 metrics_db 监控投影表与代码及 V202608051211 迁移期望的列结构（前向迁移）。
--
-- 背景：部署初始化脚本 00-create-schemas.sql 先于 Flyway 以 CREATE TABLE IF NOT EXISTS 建表，
-- 使 V202608051211 的建表语句在线上成为空操作，导致 daily_metric / monitor_alert 列名与代码不一致：
--   * monitor_alert 缺少 last_reason、version（JdbcMonitoringProjectionStore 读写、JdbcMonitoringEventStore 投影 INSERT 依赖）；
--   * monitor_alert.subject_id 误为 NOT NULL（投影 INSERT 显式写 NULL）；
--   * monitor_alert 存在 ck_monitor_alert_severity 级别检查，但投影事件可携带 CRITICAL/WARNING/INFO；
--   * daily_metric 的 business_date / definition_version 列名应为 metric_date / version。
--
-- 本迁移通过 information_schema 判断列是否存在，对“已对齐”与“漂移”两种库均幂等；
-- 不修改任何已执行迁移。仅操作运营投影，不触碰资金事实。

-- 1. monitor_alert 对齐（漂移标记：缺少 last_reason 列）
SET @alert_needs = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'metrics_db' AND TABLE_NAME = 'monitor_alert' AND COLUMN_NAME = 'last_reason'
);

-- 1.1 移除与事件投影不符的级别约束（事件可携带 CRITICAL/WARNING/INFO，见 JdbcMonitoringEventStore.projectAlert）
SET @sql = IF(@alert_needs = 0,
    'ALTER TABLE metrics_db.monitor_alert DROP CHECK ck_monitor_alert_severity', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.2 subject_id 允许为空（投影 INSERT 显式写 NULL）
SET @sql = IF(@alert_needs = 0,
    'ALTER TABLE metrics_db.monitor_alert MODIFY COLUMN subject_id VARCHAR(128) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.3 补齐 last_reason / version（与 V202608051211 的列序一致：assignee_id 之后）
SET @sql = IF(@alert_needs = 0,
    'ALTER TABLE metrics_db.monitor_alert ADD COLUMN last_reason VARCHAR(256) NULL AFTER assignee_id, '
    'ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER last_reason', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. daily_metric 对齐（漂移标记：旧列 business_date 仍存在）
SET @daily_needs = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'metrics_db' AND TABLE_NAME = 'daily_metric' AND COLUMN_NAME = 'business_date'
);

-- 2.1 先移除与 V202608051211 不一致的外键及其支撑索引（目标 schema 无该外键）
SET @sql = IF(@daily_needs > 0,
    'ALTER TABLE metrics_db.daily_metric DROP FOREIGN KEY fk_daily_metric_definition', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(@daily_needs > 0,
    'ALTER TABLE metrics_db.daily_metric DROP KEY fk_daily_metric_definition', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.2 列名对齐：business_date -> metric_date、definition_version -> version（主键与索引随列改名自动更新）
SET @sql = IF(@daily_needs > 0,
    'ALTER TABLE metrics_db.daily_metric CHANGE COLUMN business_date metric_date DATE NOT NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(@daily_needs > 0,
    'ALTER TABLE metrics_db.daily_metric CHANGE COLUMN definition_version version INT UNSIGNED NOT NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
