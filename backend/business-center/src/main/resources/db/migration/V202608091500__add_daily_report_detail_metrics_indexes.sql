CREATE INDEX idx_quality_result_report_date_status
    ON metrics_db.quality_result (task_code, data_date, status, checked_at);

CREATE INDEX idx_monitor_alert_report_time
    ON metrics_db.monitor_alert (opened_at, severity, status);

CREATE INDEX idx_analytics_event_reconciliation_time
    ON metrics_db.analytics_event (event_type, occurred_at, business_type);

-- 日报详情按业务日期读取质量、告警和分析事件；复合索引避免全表扫描影响运营查询。
