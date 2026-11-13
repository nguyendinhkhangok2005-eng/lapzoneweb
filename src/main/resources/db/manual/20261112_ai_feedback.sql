-- Apply before deployment with ddl-auto=none/validate; ddl-auto=update creates the mapped table.
CREATE TABLE ai_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    rating VARCHAR(10) NOT NULL,
    reason VARCHAR(24) NULL,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    reviewed_by BIGINT NULL,
    CONSTRAINT uk_ai_feedback_message UNIQUE (message_id),
    CONSTRAINT fk_ai_feedback_message FOREIGN KEY (message_id) REFERENCES ai_messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_feedback_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_feedback_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT chk_ai_feedback_rating CHECK (rating IN ('POSITIVE', 'NEGATIVE')),
    CONSTRAINT chk_ai_feedback_reason CHECK (
        (rating = 'POSITIVE' AND reason IS NULL) OR
        (rating = 'NEGATIVE' AND reason IN ('INCORRECT','IRRELEVANT','BAD_RECOMMENDATION','UNCLEAR','OTHER'))),
    INDEX idx_ai_feedback_created (created_at, rating),
    INDEX idx_ai_feedback_filter (rating, reason, created_at),
    INDEX idx_ai_feedback_conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
