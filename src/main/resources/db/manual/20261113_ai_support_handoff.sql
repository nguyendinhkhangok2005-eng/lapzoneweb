-- Apply once when schema management is manual. Existing support messaging tables are reused.
ALTER TABLE ai_messages ADD COLUMN support_offered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE support_conversations
    ADD COLUMN ai_source_conversation_id VARCHAR(36) NULL,
    ADD COLUMN ai_source_message_id BIGINT NULL,
    ADD CONSTRAINT uk_support_ai_source_message UNIQUE (ai_source_message_id);
-- Scalar AI references intentionally have no FK: deleting AI history must not delete support requests.
