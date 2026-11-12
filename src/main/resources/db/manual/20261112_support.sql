-- MySQL / InnoDB. Run once when schema management uses ddl-auto=none/validate.
-- The existing ddl-auto=update configuration also creates these mapped tables.
CREATE TABLE support_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    subject VARCHAR(120) NOT NULL,
    status ENUM('WAITING', 'REPLIED', 'CLOSED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_support_owner_updated (customer_id, updated_at),
    CONSTRAINT fk_support_customer FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    admin_message BOOLEAN NOT NULL,
    content TEXT NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    INDEX idx_support_message_history (conversation_id, id),
    INDEX idx_support_message_unread (conversation_id, admin_message, read_at),
    CONSTRAINT fk_support_conversation FOREIGN KEY (conversation_id) REFERENCES support_conversations(id),
    CONSTRAINT fk_support_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
