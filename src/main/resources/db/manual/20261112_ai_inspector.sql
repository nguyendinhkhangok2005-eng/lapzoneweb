-- Apply before deployment with ddl-auto=none/validate; ddl-auto=update creates the table.
CREATE TABLE ai_inspection_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NULL,
    request_token VARCHAR(36) NOT NULL,
    question VARCHAR(500) NOT NULL,
    preset VARCHAR(20) NOT NULL,
    state VARCHAR(12) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    result_json LONGTEXT NULL,
    error VARCHAR(300) NULL,
    CONSTRAINT uk_ai_inspection_token UNIQUE (request_token),
    CONSTRAINT fk_ai_inspection_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_ai_inspection_admin_started (admin_id, started_at),
    INDEX idx_ai_inspection_started (started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
