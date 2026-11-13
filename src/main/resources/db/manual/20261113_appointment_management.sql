-- Review-only MySQL / InnoDB migration. Do not run against the live database here.
-- Existing appointments columns and records are left intact. ddl-auto=update also
-- creates these mapped tables. Legacy appointments have no ownership until verified.
CREATE TABLE appointment_management (
    appointment_id BIGINT NOT NULL PRIMARY KEY,
    customer_id BIGINT NULL,
    duration_minutes INT NULL,
    updated_at DATETIME(6) NOT NULL,
    status_changed_at DATETIME(6) NOT NULL,
    status_reason VARCHAR(2000) NULL,
    INDEX idx_appointment_customer_updated (customer_id, updated_at),
    CONSTRAINT fk_appointment_management_record FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_appointment_management_customer FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(2000) NULL,
    changed_by BIGINT NULL,
    actor_role VARCHAR(16) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_appointment_history (appointment_id, id),
    CONSTRAINT fk_appointment_history_record FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_appointment_history_actor FOREIGN KEY (changed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Atomic upsert of one key per showroom/date holds an InnoDB row lock until commit,
-- including when that showroom/date has no appointments yet. No pre-seeding needed.
CREATE TABLE appointment_booking_locks (
    lock_key VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
