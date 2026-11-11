-- MySQL 8+ / InnoDB. Apply once before deployment when ddl-auto=none/validate.
-- Back up the schema first. Existing orders retain NULL voucher snapshots.
CREATE TABLE vouchers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    discount_percent INT NOT NULL,
    quantity INT NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    redeemed_count INT NOT NULL DEFAULT 0,
    per_user_limit INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    minimum_order_value DECIMAL(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_voucher_terms CHECK (discount_percent BETWEEN 1 AND 10
        AND quantity >= 0 AND reserved_count >= 0 AND redeemed_count >= 0
        AND reserved_count + redeemed_count <= quantity AND per_user_limit >= 1
        AND minimum_order_value >= 0 AND ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE voucher_products (
    voucher_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, product_id),
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;
CREATE TABLE voucher_categories (
    voucher_id BIGINT NOT NULL, category_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, category_id),
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;
CREATE TABLE voucher_users (
    voucher_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, user_id),
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;
ALTER TABLE orders
    ADD COLUMN subtotal_before_voucher DECIMAL(19,2) NULL,
    ADD COLUMN voucher_discount DECIMAL(19,2) NULL,
    ADD COLUMN voucher_code VARCHAR(40) NULL;
CREATE TABLE voucher_redemptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL UNIQUE,
    checkout_key VARCHAR(36) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    redeemed_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_voucher_user (voucher_id, user_id),
    CONSTRAINT ck_redemption_amount CHECK (discount_amount > 0),
    CONSTRAINT ck_redemption_state CHECK (state IN ('RESERVED', 'REDEEMED', 'RELEASED'))
) ENGINE=InnoDB;
