-- 日报质量允许 WARNING，以便质量门禁保留风险标记并由查询层统一过滤 FAILED/UNKNOWN。
ALTER TABLE metrics_db.daily_metric DROP CHECK ck_daily_metric_quality;
ALTER TABLE metrics_db.daily_metric ADD CONSTRAINT ck_daily_metric_quality
    CHECK (quality_status IN ('PENDING','PASSED','WARNING','FAILED','UNKNOWN'));
