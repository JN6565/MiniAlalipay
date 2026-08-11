-- 历史漂移修复（续）：实际库中 qr_pay_order 缺失初始迁移 V202608051210
-- 定义的 payer_account_id 列，补齐以对齐代码查询与写入。
ALTER TABLE business_db.qr_pay_order
    ADD COLUMN payer_account_id CHAR(26) NULL COMMENT '付款方本体账户映射' AFTER payer_user_id;
