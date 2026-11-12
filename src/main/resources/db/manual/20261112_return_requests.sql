-- MySQL / InnoDB: apply once after 20261112_order_management.sql when ddl-auto=none/validate.
-- ddl-auto=update creates these mappings automatically. No fabricated quantity/history backfill.
-- Check and resolve pre-existing duplicate item cases before adding the unique constraint:
-- SELECT order_detail_id, COUNT(*) FROM order_return_cases WHERE order_detail_id IS NOT NULL
-- GROUP BY order_detail_id HAVING COUNT(*) > 1;
ALTER TABLE order_return_cases
    ADD COLUMN quantity INT NULL,
    ADD COLUMN admin_note VARCHAR(2000) NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL,
    ADD COLUMN completed_by BIGINT NULL,
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD COLUMN completion_note VARCHAR(2000) NULL,
    ADD CONSTRAINT uq_return_purchased_line UNIQUE (order_detail_id),
    ADD CONSTRAINT ck_return_quantity CHECK (quantity IS NULL OR quantity > 0),
    ADD FOREIGN KEY (reviewed_by) REFERENCES users(id),
    ADD FOREIGN KEY (completed_by) REFERENCES users(id);

CREATE TABLE order_return_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    previous_status ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED') NULL,
    new_status ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED') NOT NULL,
    note VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_return_event_history (request_id, id),
    FOREIGN KEY (request_id) REFERENCES order_return_cases(id),
    FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
