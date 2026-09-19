-- 客服会话与消息。大厅：status='active' AND agent_id IS NULL
USE orange_market_simple;

CREATE TABLE IF NOT EXISTS service_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '发起用户 user_account.id',
    agent_id BIGINT NULL COMMENT '接单客服 user_account.id，未接单为空',
    status VARCHAR(32) NOT NULL COMMENT 'active / closed',
    closed_at DATETIME NULL COMMENT '关闭时间',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_service_session_lobby (status, agent_id, created_at),
    KEY idx_service_session_user (user_id, status),
    KEY idx_service_session_agent (agent_id, status),
    -- closed 行表达式为 NULL，UNIQUE 允许重复；同一用户同时只能有一条 active
    UNIQUE KEY uk_service_session_user_active ((CASE WHEN status = 'active' THEN user_id END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服业务会话';

CREATE TABLE IF NOT EXISTS service_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT 'service_session.id',
    sender_type VARCHAR(16) NOT NULL COMMENT 'user / agent / system',
    sender_id BIGINT NULL COMMENT '发送人 user_account.id，系统消息可空',
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_service_message_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服会话消息';
