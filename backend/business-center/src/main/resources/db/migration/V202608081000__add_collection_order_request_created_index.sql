-- 一码多收：固定收款请求可被多人多次支付，收款方订单列表按请求查询订单并按创建时间倒序展示，
-- 为 (request_id, created_at) 建复合索引覆盖该查询路径，避免全表扫描。
CREATE INDEX idx_collection_order_request_created
    ON business_db.collection_order (request_id, created_at);
