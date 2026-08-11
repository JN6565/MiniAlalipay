-- 为三类收款入口增加手动输入短码：8 位纯数字，明文存储（短码只是订单指针，
-- 付款仍需登录、支付密码与风控），唯一索引仅约束非空值，允许存量行保持 NULL。
ALTER TABLE business_db.personal_collection_code
    ADD COLUMN short_code VARCHAR(8) NULL COMMENT '手动输入收款短码，8 位纯数字',
    ADD UNIQUE KEY uk_personal_collection_code_short_code (short_code);

ALTER TABLE business_db.collection_request
    ADD COLUMN short_code VARCHAR(8) NULL COMMENT '手动输入收款短码，8 位纯数字',
    ADD UNIQUE KEY uk_collection_request_short_code (short_code);

ALTER TABLE business_db.qr_pay_order
    ADD COLUMN short_code VARCHAR(8) NULL COMMENT '手动输入收款短码，8 位纯数字',
    ADD UNIQUE KEY uk_qr_pay_order_short_code (short_code);
