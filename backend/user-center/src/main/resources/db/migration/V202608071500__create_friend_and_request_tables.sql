-- 好友请求表
CREATE TABLE IF NOT EXISTS user_db.friend_request (
    request_id CHAR(26) NOT NULL PRIMARY KEY,
    from_user_id CHAR(26) NOT NULL,
    to_user_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    message VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_from_to (from_user_id, to_user_id),
    KEY idx_to_user_status (to_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 好友关系表（双向）
CREATE TABLE IF NOT EXISTS user_db.friend (
    user_id CHAR(26) NOT NULL,
    friend_user_id CHAR(26) NOT NULL,
    alias VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, friend_user_id),
    KEY idx_friend_user (friend_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
