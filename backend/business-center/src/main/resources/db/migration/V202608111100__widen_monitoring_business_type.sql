-- 加宽监控投影表 business_type 列，容纳 BANK_CARD_WITHDRAW（19 字符）等更长业务类型。
-- 背景：V202608101830 只加宽了 business_db.fund_transaction.business_type，
-- 而 metrics_db 的 analytics_event 与 monitoring_transaction_final_projection 两表仍为 VARCHAR(16)；
-- 提现/充值事件投影时超长业务类型触发 Data too long，卡死监控消费游标并拖垮可信看板今日交易额。
ALTER TABLE metrics_db.analytics_event
MODIFY COLUMN business_type VARCHAR(32) NULL COMMENT '业务类型：TRANSFER/QR_PAY/RECHARGE/REFUND/CREDIT_PAY/COLLECTION/BANK_CARD_RECHARGE/BANK_CARD_WITHDRAW';
ALTER TABLE metrics_db.monitoring_transaction_final_projection
MODIFY COLUMN business_type VARCHAR(32) NULL COMMENT '业务类型：TRANSFER/QR_PAY/RECHARGE/REFUND/CREDIT_PAY/COLLECTION/BANK_CARD_RECHARGE/BANK_CARD_WITHDRAW';
