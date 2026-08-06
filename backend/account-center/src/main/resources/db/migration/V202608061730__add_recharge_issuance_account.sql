-- 充值复式记账需要系统发行权益科目作为借方，用户余额负债科目作为贷方。
-- 使用幂等插入，保证新环境和已有环境重复执行不会产生重复科目。
INSERT INTO ledger_db.ledger_account
    (ledger_account_id, owner_type, owner_id, account_code, account_type, account_class,
     normal_direction, currency, status, created_at, updated_at)
SELECT '01JZ8Q8Y7K7K7K7K7K7K7K7K7K', 'SYSTEM', 'SYSTEM_ISSUANCE',
       'SYSTEM_ISSUANCE_EQUITY', 'SYSTEM_ISSUANCE_EQUITY', 'EQUITY', 'CREDIT', 'CNY', 'ACTIVE',
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_db.ledger_account
    WHERE owner_type='SYSTEM' AND owner_id='SYSTEM_ISSUANCE'
      AND account_type='SYSTEM_ISSUANCE_EQUITY' AND currency='CNY'
);
