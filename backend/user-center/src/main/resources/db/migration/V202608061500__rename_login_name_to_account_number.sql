-- 登录名已经被系统生成的账户号取代，数据库列名同步表达真实业务语义。
-- 通过向前迁移保留全部历史账户号，不修改已经执行过的建表迁移。
ALTER TABLE user_db.app_user
    RENAME COLUMN login_name TO account_number,
    RENAME INDEX uk_app_user_login_name TO uk_app_user_account_number;

