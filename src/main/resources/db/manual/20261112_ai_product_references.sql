-- Apply before deployment when schema management uses ddl-auto=none/validate.
-- Existing conversations/messages are preserved; legacy messages keep NULL references.
ALTER TABLE ai_messages ADD COLUMN product_ids VARCHAR(100) NULL;
