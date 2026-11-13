-- Apply before deployment with ddl-auto=none/validate; ddl-auto=update creates these tables.
CREATE TABLE ai_knowledge_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    state VARCHAR(12) NOT NULL,
    current_version INT NOT NULL,
    published_version INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_ai_knowledge_state (state, updated_at),
    CONSTRAINT chk_ai_knowledge_state CHECK (state IN ('DRAFT','PUBLISHED','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_knowledge_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    keywords VARCHAR(300) NOT NULL,
    search_text TEXT NOT NULL,
    category VARCHAR(20) NOT NULL,
    action VARCHAR(12) NOT NULL,
    editor_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_ai_knowledge_version UNIQUE (entry_id, version_no),
    CONSTRAINT fk_ai_knowledge_entry FOREIGN KEY (entry_id) REFERENCES ai_knowledge_entries(id),
    CONSTRAINT fk_ai_knowledge_editor FOREIGN KEY (editor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
