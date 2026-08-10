-- 更新 fund_transaction 表的 CHECK 约束以支持银行卡充值/提现业务类型。
-- 背景：V202608101300 增加了 bank_card_id 列，V202608101830 扩展了 business_type 列长度，
-- 但多个 CHECK 约束未同步更新，导致银行卡交易插入时约束违反。

-- 1. 更新 business_type 约束：增加 BANK_CARD_RECHARGE 和 BANK_CARD_WITHDRAW
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_business_type;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_business_type CHECK (
    business_type IN ('TRANSFER','QR_PAY','CREDIT_PAY','CREDIT_REPAY','RECHARGE','REFUND',
                      'BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW')
);

-- 2. 更新 funding_source 约束：增加 BANK_CARD
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_funding_source;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_funding_source CHECK (
    funding_source IN ('BALANCE','MINI_CREDIT','SYSTEM_ISSUANCE','BANK_CARD')
);

-- 3. 更新 source_type 约束：增加银行卡订单类型
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_source_type;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_source_type CHECK (
    source_type IN ('TRANSFER_DRAFT','QR_PAY_ORDER','PERSONAL_QR_ORDER',
                    'COLLECTION_REQUEST_ORDER','CREDIT_REPAYMENT_DRAFT',
                    'RECHARGE_ORDER','REFUND_ORDER',
                    'BANK_CARD_RECHARGE_ORDER','BANK_CARD_WITHDRAW_ORDER')
);

-- 4. 更新 business_source 联合约束：增加银行卡业务与订单类型的映射
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_business_source;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_business_source CHECK (
    (business_type = 'TRANSFER' AND source_type IN ('TRANSFER_DRAFT','PERSONAL_QR_ORDER','COLLECTION_REQUEST_ORDER'))
    OR (business_type = 'QR_PAY' AND source_type = 'QR_PAY_ORDER')
    OR (business_type = 'CREDIT_PAY' AND source_type IN ('QR_PAY_ORDER','PERSONAL_QR_ORDER','COLLECTION_REQUEST_ORDER'))
    OR (business_type = 'CREDIT_REPAY' AND source_type = 'CREDIT_REPAYMENT_DRAFT')
    OR (business_type = 'RECHARGE' AND source_type = 'RECHARGE_ORDER')
    OR (business_type = 'REFUND' AND source_type = 'REFUND_ORDER')
    OR (business_type = 'BANK_CARD_RECHARGE' AND source_type = 'BANK_CARD_RECHARGE_ORDER')
    OR (business_type = 'BANK_CARD_WITHDRAW' AND source_type = 'BANK_CARD_WITHDRAW_ORDER')
);

-- 5. 更新 business_funding 联合约束：增加银行卡业务与资金来源的映射
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_business_funding;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_business_funding CHECK (
    (business_type IN ('TRANSFER','QR_PAY','CREDIT_REPAY') AND funding_source = 'BALANCE')
    OR (business_type = 'CREDIT_PAY' AND funding_source = 'MINI_CREDIT')
    OR (business_type = 'RECHARGE' AND funding_source = 'SYSTEM_ISSUANCE')
    OR (business_type = 'REFUND' AND funding_source IN ('BALANCE','MINI_CREDIT'))
    OR (business_type IN ('BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW') AND funding_source = 'BANK_CARD')
);

-- 6. 更新 accounts 约束：银行卡交易允许 payer_account_id 为 NULL（充值）或 payee 为银行卡 ID
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_accounts;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_accounts CHECK (
    (business_type = 'RECHARGE' AND payer_account_id IS NULL AND funding_source = 'SYSTEM_ISSUANCE')
    OR (business_type IN ('BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW') AND funding_source = 'BANK_CARD')
    OR (business_type NOT IN ('RECHARGE','BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW')
        AND payer_account_id IS NOT NULL
        AND payer_account_id <> payee_account_id
        AND funding_source <> 'SYSTEM_ISSUANCE')
);

-- 7. 更新 related 约束：银行卡交易不需要关联交易编号
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_related;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_related CHECK (
    (business_type = 'REFUND' AND related_transaction_id IS NOT NULL)
    OR (business_type IN ('TRANSFER','QR_PAY','CREDIT_PAY','CREDIT_REPAY','RECHARGE','BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW') AND related_transaction_id IS NULL)
);
