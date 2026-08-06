-- 阶段五修正：恢复 C2C 来源订单的权威资金终态取值。
-- V202608060900 为增加 RISK_REVIEW 而收紧状态约束时，误删了 SUCCESS/FAILED/MANUAL_REVIEW；
-- 但统一交易终态发布器 JdbcBusinessStore.projectCollectionTerminalState 仍会在同一
-- business_db 事务内把 SUCCESS/MANUAL_REVIEW 回填到 collection_order。若约束不含这些终态，
-- 生产 MySQL 会因 CHECK 约束拒绝写入并回滚，导致成功支付与人工审核事实无法落库。
-- 本迁移为向前修正，禁止修改或回滚已执行的 V202608060900；终态仅允许统一交易发布器回填。
ALTER TABLE business_db.collection_order DROP CHECK ck_collection_order_status;
ALTER TABLE business_db.collection_order
    ADD CONSTRAINT ck_collection_order_status CHECK (
        status IN ('DRAFT','PENDING_CONFIRMATION','RISK_REVIEW','PROCESSING',
                   'SUCCESS','FAILED','MANUAL_REVIEW','CANCELLED','EXPIRED')
    );
