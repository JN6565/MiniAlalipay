-- C2C 付款固定使用余额；该列让数据库与系统分析一致，防止任何绕过应用层的信用来源写入。
ALTER TABLE business_db.collection_order
    ADD COLUMN funding_source VARCHAR(16) NOT NULL DEFAULT 'BALANCE' AFTER subject;

ALTER TABLE business_db.collection_order
    ADD CONSTRAINT ck_collection_order_funding CHECK (funding_source = 'BALANCE');

-- 订单终态由统一交易终态发布器回填，本约束仅允许其表达权威资金事实，不允许 Controller 自行写成功。
ALTER TABLE business_db.collection_order DROP CHECK ck_collection_order_status;
ALTER TABLE business_db.collection_order
    ADD CONSTRAINT ck_collection_order_status CHECK (
        status IN ('DRAFT','PENDING_CONFIRMATION','PROCESSING','SUCCESS','FAILED','MANUAL_REVIEW','CANCELLED','EXPIRED')
    );
