-- 为 Mini 花呗增加用户显式开通事实。
-- opened_at 为空表示账户中心仅在开户注册时预创建了固定演示额度，用户尚未确认开通，信用支付必须拒绝。
ALTER TABLE account_db.credit_account
    ADD COLUMN opened_at DATETIME(3) NULL COMMENT '用户显式开通 Mini 花呗的时间；NULL 表示未开通'
        AFTER suspend_reason;

CREATE INDEX idx_credit_account_opened_status
    ON account_db.credit_account (opened_at, status, updated_at);
