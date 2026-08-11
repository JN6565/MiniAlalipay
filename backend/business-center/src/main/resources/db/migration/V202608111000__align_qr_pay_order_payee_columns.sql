-- 历史漂移修复：实际库中 qr_pay_order 的收款方列曾被手工改为 merchant_user_id/merchant_account_id，
-- 与代码及初始迁移 V202608051210 定义的 payee_user_id/payee_account_id 不一致，
-- 导致动态订单短码兑换等查询报 Unknown column。此处将列名对齐回代码约定。
ALTER TABLE business_db.qr_pay_order
    CHANGE COLUMN merchant_user_id payee_user_id CHAR(26) NOT NULL COMMENT '收款方用户',
    CHANGE COLUMN merchant_account_id payee_account_id CHAR(26) NOT NULL COMMENT '收款方本体账户';
