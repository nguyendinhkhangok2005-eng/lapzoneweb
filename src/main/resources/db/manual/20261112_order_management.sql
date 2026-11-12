-- MySQL / InnoDB: run once only when using ddl-auto=none/validate.
-- Existing ddl-auto=update also creates the new entity tables. No order data/status backfill.
CREATE TABLE order_status_changes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    previous_status VARCHAR(255) NULL,
    new_status VARCHAR(255) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_order_status_history (order_id, id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_detail_id BIGINT NOT NULL UNIQUE,
    rating INT NOT NULL,
    content VARCHAR(4000) NULL,
    created_at DATETIME(6) NOT NULL,
    CHECK (rating BETWEEN 1 AND 5),
    FOREIGN KEY (order_detail_id) REFERENCES order_details(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_return_cases (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_detail_id BIGINT NULL,
    type ENUM('RETURN', 'REFUND') NOT NULL,
    status ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED') NOT NULL,
    reason VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_order_return_cases (order_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (order_detail_id) REFERENCES order_details(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
