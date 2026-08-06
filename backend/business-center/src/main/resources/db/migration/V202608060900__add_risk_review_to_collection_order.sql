-- 阶段五风控联动：C2C 来源订单新增受理前人工复核状态，命中转人工规则时订单停留在 RISK_REVIEW，不创建资金交易。
ALTER TABLE business_db.collection_order DROP CHECK ck_collection_order_status;
ALTER TABLE business_db.collection_order
    ADD CONSTRAINT ck_collection_order_status CHECK (status IN ('DRAFT','PENDING_CONFIRMATION','RISK_REVIEW','PROCESSING','CANCELLED','EXPIRED'));
