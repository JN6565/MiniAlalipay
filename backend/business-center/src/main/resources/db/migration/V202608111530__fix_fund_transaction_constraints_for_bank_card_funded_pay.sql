-- 修复约束缺口：银行卡出资的转账/扫码支付（TRANSFER/QR_PAY + funding_source=BANK_CARD）。
-- 领域聚合 FundTransaction 要求该组合 payer_account_id 为空（资金从银行卡虚拟余额扣减，
-- 不占用付款方本体账户余额），但 V202608101845 只为 BANK_CARD_RECHARGE/BANK_CARD_WITHDRAW
-- 放开约束，导致银行卡出资支付受理插入 fund_transaction 时违反约束（MySQL 3819）失败。
-- 本次同步放宽两处联合约束，语义与 FundTransaction 构造器不变量保持一致：
-- 银行卡出资的 TRANSFER/QR_PAY 付款账户必须为空；其余非充值业务仍要求付款账户非空且收付不同。

-- 1. accounts 约束：新增银行卡出资 TRANSFER/QR_PAY 付款账户为空的合法分支
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_accounts;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_accounts CHECK (
    (business_type = 'RECHARGE' AND payer_account_id IS NULL AND funding_source = 'SYSTEM_ISSUANCE')
    OR (business_type IN ('BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW') AND funding_source = 'BANK_CARD')
    OR (business_type IN ('TRANSFER','QR_PAY') AND funding_source = 'BANK_CARD' AND payer_account_id IS NULL)
    OR (business_type NOT IN ('RECHARGE','BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW')
        AND NOT (business_type IN ('TRANSFER','QR_PAY') AND funding_source = 'BANK_CARD')
        AND payer_account_id IS NOT NULL
        AND payer_account_id <> payee_account_id
        AND funding_source <> 'SYSTEM_ISSUANCE')
);

-- 2. business_funding 联合约束：TRANSFER/QR_PAY 的合法资金来源增加 BANK_CARD
ALTER TABLE business_db.fund_transaction DROP CHECK ck_fund_transaction_business_funding;
ALTER TABLE business_db.fund_transaction ADD CONSTRAINT ck_fund_transaction_business_funding CHECK (
    (business_type IN ('TRANSFER','QR_PAY') AND funding_source IN ('BALANCE','BANK_CARD'))
    OR (business_type = 'CREDIT_REPAY' AND funding_source = 'BALANCE')
    OR (business_type = 'CREDIT_PAY' AND funding_source = 'MINI_CREDIT')
    OR (business_type = 'RECHARGE' AND funding_source = 'SYSTEM_ISSUANCE')
    OR (business_type = 'REFUND' AND funding_source IN ('BALANCE','MINI_CREDIT'))
    OR (business_type IN ('BANK_CARD_RECHARGE','BANK_CARD_WITHDRAW') AND funding_source = 'BANK_CARD')
);
