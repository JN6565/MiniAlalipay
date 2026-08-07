-- B 端管理冻结审计列。
-- 管理冻结（status -> DISABLED）时记录操作者与冻结理由，解冻时清空。
-- 仅保存操作者 ID 与理由文本，不保存任何密码或敏感原值。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 5.1 节。

ALTER TABLE user_db.app_user
    ADD COLUMN disabled_by CHAR(26) NULL COMMENT '管理冻结操作者用户 ID' AFTER updated_at,
    ADD COLUMN disabled_reason VARCHAR(200) NULL COMMENT '管理冻结理由' AFTER disabled_by;
