-- 用户 ID 日序列表：按日期保存当天已分配的序列值，保证用户编号生成可并发递增。
CREATE TABLE IF NOT EXISTS user_db.user_id_sequence (
    seq_date DATE NOT NULL,
    current_value INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (seq_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
