-- 为失败事件保留可追溯原因和重试窗口，避免质量门禁只能显示 FAILED 而无法定位影响范围。
ALTER TABLE metrics_db.inbox_event
    ADD COLUMN failure_reason VARCHAR(512) NULL,
    ADD COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at DATETIME(3) NULL,
    ADD COLUMN last_failed_at DATETIME(3) NULL;

CREATE INDEX idx_inbox_event_retry
    ON metrics_db.inbox_event (status, next_retry_at, updated_at);
