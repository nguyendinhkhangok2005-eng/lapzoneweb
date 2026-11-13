-- Apply only when schema management uses ddl-auto=none/validate.
-- Inspect existing indexes first if Hibernate schema update has already run.
CREATE INDEX idx_ai_conversation_created ON ai_conversations (created_at, id);
CREATE INDEX idx_ai_message_created ON ai_messages (created_at, role);
