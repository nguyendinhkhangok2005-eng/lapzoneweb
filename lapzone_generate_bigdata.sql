-- ============================================================
--  LAPZONE - SINH DỮ LIỆU LỚN (PHIÊN BẢN TỐI ƯU - KHÔNG TIMEOUT)
--  Dùng BULK INSERT thay cho loop từng dòng
--  Chạy sau lapzone_init_official.sql
-- ============================================================

USE lapzone_db;

-- ============================================================
-- BƯỚC 0: TĂNG TIMEOUT TRƯỚC KHI CHẠY
-- ============================================================
SET SESSION wait_timeout         = 28800;  -- 8 tiếng
SET SESSION interactive_timeout  = 28800;
SET SESSION net_read_timeout     = 3600;
SET SESSION net_write_timeout    = 3600;
SET SESSION max_execution_time   = 0;      -- Không giới hạn (MySQL 5.7.8+)

SET FOREIGN_KEY_CHECKS = 0;
SET autocommit          = 0;
SET unique_checks       = 0;
SET GLOBAL innodb_flush_log_at_trx_commit = 2;   -- Giảm I/O ghi log (GLOBAL variable)

-- ============================================================
-- BƯỚC 0b: DỌN DẸP DỮ LIỆU CŨ (nếu đã chạy script trước)
-- Giữ lại data gốc của products, categories, product_details
-- ============================================================
TRUNCATE TABLE cart_items;    -- Xóa giỏ hàng cũ
TRUNCATE TABLE order_details; -- Xóa chi tiết đơn hàng cũ
TRUNCATE TABLE orders;        -- Xóa đơn hàng cũ
DELETE FROM users WHERE email LIKE 'user%@lapzone.vn';  -- Xóa users bigdata
COMMIT;
SELECT '🗑️ Đã dọn dẹp dữ liệu cũ xong' AS cleanup_status;

-- ============================================================
-- BƯỚC 1: TẠO 100.000 USERS
-- Dùng stored procedure với BULK INSERT 500 dòng/lần
-- ============================================================
DROP PROCEDURE IF EXISTS gen_users;
DELIMITER $$
CREATE PROCEDURE gen_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE bulk_sql LONGTEXT;

    WHILE i <= 100000 DO
        -- Xây dựng 1 câu INSERT với 500 VALUES
        SET bulk_sql = 'INSERT IGNORE INTO users (full_name, email, phone, password, address, role) VALUES ';
        SET @rows = 0;
        
        WHILE @rows < 500 AND i <= 100000 DO
            IF @rows > 0 THEN
                SET bulk_sql = CONCAT(bulk_sql, ',');
            END IF;
            SET bulk_sql = CONCAT(bulk_sql,
                '(', QUOTE(CONCAT('Khách hàng ', i)), ',',
                     QUOTE(CONCAT('user', i, '@lapzone.vn')), ',',
                     QUOTE(CONCAT('09', LPAD(FLOOR(RAND()*100000000), 8, '0'))), ',',
                     QUOTE('$2a$10$hashedpassword.placeholder.value.here'), ',',
                     QUOTE(CONCAT(FLOOR(RAND()*999)+1,' Đường Lê Lợi, Q.',FLOOR(RAND()*12)+1,', TP.HCM')), ',',
                     QUOTE('USER'), ')'
            );
            SET @rows = @rows + 1;
            SET i = i + 1;
        END WHILE;

        SET @sql = bulk_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        COMMIT;
    END WHILE;

    SELECT CONCAT('✅ Đã tạo xong 100.000 users') AS result;
END$$
DELIMITER ;

CALL gen_users();
DROP PROCEDURE IF EXISTS gen_users;

-- ============================================================
-- BƯỚC 2: TẠO 500.000 ORDERS
-- Dùng bảng tạm + INSERT...SELECT để tạo hàng loạt siêu nhanh
-- ============================================================

-- Tạo bảng số phụ trợ (helper: 1 đến 1000)
DROP TABLE IF EXISTS helper_nums;
CREATE TABLE helper_nums (n INT PRIMARY KEY);
INSERT INTO helper_nums (n)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT n FROM seq;

COMMIT;

-- Tạo 500.000 orders = 500 lần × 1000 hàng
DROP PROCEDURE IF EXISTS gen_orders_bulk;
DELIMITER $$
CREATE PROCEDURE gen_orders_bulk()
BEGIN
    DECLARE batch INT DEFAULT 0;
    DECLARE status_list VARCHAR(200);

    WHILE batch < 500 DO
        INSERT INTO orders (total_amount, status, order_date, customer_name,
                            customer_phone, address, user_id, payment_method)
        SELECT
            ROUND(RAND() * 80000000 + 5000000, 0),
            ELT(FLOOR(RAND() * 5) + 1,
                'CHỜ XÁC NHẬN','ĐANG XỬ LÝ','ĐANG GIAO','HOÀN THÀNH','ĐÃ HỦY'),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 730) DAY),
            CONCAT('Khách hàng ', FLOOR(RAND() * 100000) + 1),
            CONCAT('09', LPAD(FLOOR(RAND() * 100000000), 8, '0')),
            CONCAT(FLOOR(RAND()*999)+1,' Đường Nguyễn Huệ, Q.',FLOOR(RAND()*12)+1,', TP.HCM'),
            FLOOR(RAND() * 100000) + 1,
            ELT(FLOOR(RAND() * 3) + 1, 'COD', 'VNPAY', 'MOMO')
        FROM helper_nums;   -- 1000 hàng mỗi lần

        COMMIT;
        SET batch = batch + 1;
    END WHILE;

    SELECT CONCAT('✅ Đã tạo xong 500.000 orders') AS result;
END$$
DELIMITER ;

CALL gen_orders_bulk();
DROP PROCEDURE IF EXISTS gen_orders_bulk;

-- ============================================================
-- BƯỚC 3: TẠO 500.000 ORDER_DETAILS
-- Tương tự: 500 batch × 1000 hàng
-- ============================================================
DROP PROCEDURE IF EXISTS gen_order_details_bulk;
DELIMITER $$
CREATE PROCEDURE gen_order_details_bulk()
BEGIN
    DECLARE batch INT DEFAULT 0;
    DECLARE max_oid BIGINT;

    SELECT MAX(id) INTO max_oid FROM orders;

    WHILE batch < 500 DO
        INSERT INTO order_details (quantity, price, order_id, product_id)
        SELECT
            FLOOR(RAND() * 3) + 1,
            ROUND(RAND() * 70000000 + 10000000, 0),
            FLOOR(RAND() * max_oid) + 1,
            ELT(FLOOR(RAND() * 22) + 1,
                1,2,3,4,5,6,7,8,9,10,
                11,12,13,14,15,16,17,18,19,20,21,22)
        FROM helper_nums;

        COMMIT;
        SET batch = batch + 1;
    END WHILE;

    SELECT CONCAT('✅ Đã tạo xong 500.000 order_details') AS result;
END$$
DELIMITER ;

CALL gen_order_details_bulk();
DROP PROCEDURE IF EXISTS gen_order_details_bulk;

-- ============================================================
-- BƯỚC 4: TẠO 100.000 CART_ITEMS
-- ============================================================
DROP PROCEDURE IF EXISTS gen_cart_items_bulk;
DELIMITER $$
CREATE PROCEDURE gen_cart_items_bulk()
BEGIN
    DECLARE batch INT DEFAULT 0;

    WHILE batch < 100 DO
        INSERT IGNORE INTO cart_items (quantity, user_id, product_id)
        SELECT
            FLOOR(RAND() * 4) + 1,
            FLOOR(RAND() * 100000) + 1,
            ELT(FLOOR(RAND() * 22) + 1,
                1,2,3,4,5,6,7,8,9,10,
                11,12,13,14,15,16,17,18,19,20,21,22)
        FROM helper_nums;

        COMMIT;
        SET batch = batch + 1;
    END WHILE;

    SELECT CONCAT('✅ Đã tạo xong 100.000 cart_items') AS result;
END$$
DELIMITER ;

CALL gen_cart_items_bulk();
DROP PROCEDURE IF EXISTS gen_cart_items_bulk;

-- ============================================================
-- DỌN DẸP & KHÔI PHỤC
-- ============================================================
DROP TABLE IF EXISTS helper_nums;

SET FOREIGN_KEY_CHECKS               = 1;
SET autocommit                        = 1;
SET unique_checks                     = 1;
SET GLOBAL innodb_flush_log_at_trx_commit = 1;   -- Khôi phục về mặc định (GLOBAL variable)

-- ============================================================
-- KIỂM TRA KẾT QUẢ CUỐI
-- ============================================================
SELECT 'users'          AS `Bảng`, FORMAT(COUNT(*), 0) AS `Số bản ghi` FROM users
UNION ALL
SELECT 'products',        FORMAT(COUNT(*), 0) FROM products
UNION ALL
SELECT 'orders',          FORMAT(COUNT(*), 0) FROM orders
UNION ALL
SELECT 'order_details',   FORMAT(COUNT(*), 0) FROM order_details
UNION ALL
SELECT 'cart_items',      FORMAT(COUNT(*), 0) FROM cart_items;
