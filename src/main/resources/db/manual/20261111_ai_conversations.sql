-- Run against the existing LAPZONE MySQL schema before deploying with ddl-auto=validate/none.
-- Additive and safe to rerun. No existing tables or records are replaced.
CREATE TABLE IF NOT EXISTS ai_conversations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NULL,
    guest_key VARCHAR(36) NULL,
    title VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ai_conversation_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    INDEX idx_ai_conversation_owner (owner_id, updated_at),
    INDEX idx_ai_conversation_guest (guest_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(8) NOT NULL,
    text LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id),
    INDEX idx_ai_message_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
