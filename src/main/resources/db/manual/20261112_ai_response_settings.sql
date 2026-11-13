-- Apply before deployment when schema management uses ddl-auto=none/validate.
-- With ddl-auto=update, Hibernate creates the two tables. No API keys are stored.
CREATE TABLE ai_response_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL,
    style VARCHAR(16) NOT NULL,
    response_length VARCHAR(12) NOT NULL,
    max_recommendations INT NOT NULL,
    highlight_promotions BOOLEAN NOT NULL,
    suggest_related_products BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    CONSTRAINT chk_ai_setting_max CHECK (max_recommendations BETWEEN 1 AND 5),
    CONSTRAINT chk_ai_setting_style CHECK (style IN ('FRIENDLY','PROFESSIONAL','CONCISE')),
    CONSTRAINT chk_ai_setting_length CHECK (response_length IN ('SHORT','MEDIUM','DETAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_response_settings_changes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(8) NOT NULL,
    before_value VARCHAR(500) NOT NULL,
    after_value VARCHAR(500) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_ai_setting_change_time (changed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
