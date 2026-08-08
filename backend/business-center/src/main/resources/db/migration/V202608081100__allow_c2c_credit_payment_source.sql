-- 扩展 C2C 收款订单的 Mini 花呗交易来源白名单，保持业务类型与资金来源的一致性。
-- 通过信息架构判断后再删除旧约束，兼容已部署旧结构和全新初始化结构。
SET @drop_business_source_constraint = IF(
    (SELECT COUNT(*)
       FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = 'business_db'
        AND TABLE_NAME = 'fund_transaction'
        AND CONSTRAINT_NAME = 'ck_fund_transaction_business_source'
        AND CONSTRAINT_TYPE = 'CHECK') > 0,
    'ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_business_source',
    'SELECT 1'
);
PREPARE drop_business_source_constraint_stmt FROM @drop_business_source_constraint;
EXECUTE drop_business_source_constraint_stmt;
DEALLOCATE PREPARE drop_business_source_constraint_stmt;

ALTER TABLE business_db.fund_transaction
    ADD CONSTRAINT ck_fund_transaction_business_source CHECK (
        (business_type = 'TRANSFER' AND
            source_type IN ('TRANSFER_DRAFT', 'PERSONAL_QR_ORDER',
                            'COLLECTION_REQUEST_ORDER')) OR
        (business_type = 'QR_PAY' AND source_type = 'QR_PAY_ORDER') OR
        (business_type = 'CREDIT_PAY' AND
            source_type IN ('QR_PAY_ORDER', 'PERSONAL_QR_ORDER',
                            'COLLECTION_REQUEST_ORDER')) OR
        (business_type = 'CREDIT_REPAY' AND source_type = 'CREDIT_REPAYMENT_DRAFT') OR
        (business_type = 'RECHARGE' AND source_type = 'RECHARGE_ORDER') OR
        (business_type = 'REFUND' AND source_type = 'REFUND_ORDER')
    );
