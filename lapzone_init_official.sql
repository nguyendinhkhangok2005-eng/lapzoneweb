DROP DATABASE IF EXISTS lapzone_db;
CREATE DATABASE IF NOT EXISTS lapzone_db;
USE lapzone_db;

-- 1. Bảng users
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    password VARCHAR(255) NOT NULL,
    address TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER'
);

-- 2. Bảng categories
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
INSERT INTO categories (id, name) VALUES
(1, 'Acer'),
(2, 'Gigabyte'),
(3, 'Apple'),
(4, 'Asus'),
(5, 'Dell'),
(6, 'HP'),
(7, 'Lenovo');


-- 3. Bảng products
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DOUBLE NOT NULL,
    stock INT NOT NULL,
    image VARCHAR(255),
    category_id BIGINT NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);
INSERT INTO products (id, name, price, stock, image, category_id) VALUES
(1, 'Dell G15 5530 2023 i5-13450HX RAM 16GB SSD 512GB RTX 3050 15.6" FHD 120Hz', 22990000.00, 2, 'assets/Dell_gaming_G15_5530_img0.jpg', 5),
(2, 'Gigabyte Gaming A16 GA6H CMHH2VN893SH i5-13420H RAM 16GB SSD 512GB RTX 4050 16" FHD+ 165Hz', 22990000.00, 3, 'assets/gigabyte_gaming_a16_img0.png', 2),
(3, 'Lenovo IdeaPad Slim 3 15IRH10 2025 i5 13420H RAM 16GB SSD 512GB 15.1" 2.5K OLED 165Hz', 17890000.00, 1, 'assets/ideapad_slim3_15irh10_img0.png', 7),
(4, 'Thinkbook 14 G7+ 2025 Ultra 5 225H RAM 32GB SSD 1TB 14.5" 3K 120Hz', 21590000.00, 2, 'assets/thinkbook_14_g7_img0.jpg', 7),
(5, 'Acer Shadow Knight 6 i7 14650HX RAM 16GB SSD 1TB RTX 4060 16" 2.5K 240Hz', 25990000.00, 1, 'assets/acer_sadow_desc_img0.png', 1),
(6, 'Lenovo IdeaPad 5 Pro 14 GT 2025 Ryzen AI 9 365 RAM 32GB SSD 1TB 14" 2.8K OLED 120Hz', 26190000.00, 1, 'assets/ideapad_5pro_14gt_img0.jpg', 7),
(7, 'Lenovo IdeaPad Slim 5 2025 Ryzen 7 H 255 RAM 24GB SSD 1TB 2.8K OLED 120Hz', 18490000.00, 2, 'assets/ideapad_slim5_2025_img0.png', 7),
(8, 'Lenovo Legion Y7000P 16IRX10 2025 i9 14900HX RAM 16GB SSD 1TB RTX 5060 16" 2.5K 240Hz', 34490000.00, 1, 'assets/y7000_2025_img0.jpg', 7),
(9, 'Lenovo IdeaPad 5 Pro 16 GT 2025 Ultra 5 225H RAM 32GB SSD 1TB 16" 2.8K OLED 120Hz', 23900000.00, 1, 'assets/ideapad_5pro_16gt_img0.jpg', 7),
(10, 'Gigabyte Gaming A16 GA6H CMHH2VN893SH i5-13420H RAM 16GB SSD 512GB RTX 4050 16" FHD+ 165Hz', 22990000.00, 3, 'assets/gigabyte_gaming_a16_img0.png', 2),
(11, 'Lenovo Legion 5 Y7000 15IRX10 2025 i7 14650HX RAM 16GB SSD 512GB RTX 5060 15.3" 2.5K 180Hz', 30900000.00, 2, 'assets/y7000_2025_img0.jpg', 7),
(12, 'Lenovo Legion Pro 5 R9000P 16ADR10 2025 Ryzen 9 8945HX RAM 32GB SSD 1TB RTX 5060 16" 2.5K 240Hz', 34990000.00, 1, 'assets/legion_pro5_r9000p_img0.jpg', 7),
(13, 'Lenovo IdeaPad Slim 3 15IRH10 2025 i5 13420H RAM 16GB SSD 512GB 15.1" 2.5K OLED 165Hz', 17890000.00, 0, 'assets/ideapad_slim3_15irh10_img0.png', 7),
(14, 'Thinkbook 14 G7+ 2025 Ultra 5 225H RAM 32GB SSD 1TB 14.5" 3K 120Hz', 21590000.00, 1, 'assets/thinkbook_14_g7_img0.jpg', 7),
(15, 'Macbook Pro 14 M2 Pro 10 Core RAM 16GB SSD 512GB 16 GPU Liquid Retina', 34700000.00, 2, 'assets/macbook_pro14_M2_img0.jpg', 3),
(16, 'Macbook Pro 16 M1 Pro RAM 16GB SSD 512GB Liquid Retina 2021', 31500000.00, 2, 'assets/macbook_pro_16m1_img0.jpg', 3),
(17, 'Macbook Pro 14 M1 Pro 10-core CPU 16-core GPU RAM 16GB SSD 512GB Retina 2021', 30200000.00, 2, 'assets/macbook_pro_14_m1_img0.png', 3),
(18, 'Macbook Pro 14 M1 Pro 8-Core CPU 14-Core GPU RAM 16GB SSD 512GB Retina 2021 - Bảo Hành 03 tháng', 27500000.00, 2, 'assets/macbook_pro_14_m1_img0.png', 3),
(19, 'Macbook Pro 16 M1 Pro RAM 16GB SSD 512GB Liquid Retina 2021', 31500000.00, 2, 'assets/macbook_pro_16m1_img0.jpg', 3),
(20, 'acer nitroV sieu cap', 25000000.00, 1, 'assets/idea_padpro_14gt.jpg', 1),
(21, 'Dell Precision 5580', 35000000.00, 1, 'assets/dohoakythuat.webp', 5),
(22, 'acer nitroV', 23000000.00, 1, 'assets/acer_sadow_desc_img0.png', 1);


-- 4. Bảng product_details (Nâng cấp full thông số Laptop)
CREATE TABLE product_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cpu VARCHAR(255),          -- Ví dụ: Intel Core i7-13700H
    ram VARCHAR(255),          -- Ví dụ: 16GB DDR5 4800MHz
    storage VARCHAR(255),      -- Ví dụ: 512GB SSD NVMe PCIe
    gpu VARCHAR(255),          -- Ví dụ: NVIDIA GeForce RTX 4060 8GB
    screen VARCHAR(255),       -- Ví dụ: 15.6 inch FHD (1920x1080) 144Hz
    battery VARCHAR(255),      -- Ví dụ: 4 Cell, 60Wh
    audio VARCHAR(255),        -- Ví dụ: Dolby Atmos, Dual Speakers
    ports VARCHAR(255),        -- Ví dụ: 2x USB 3.2, 1x Type-C, 1x HDMI
    wireless VARCHAR(255),     -- Ví dụ: Wi-Fi 6E, Bluetooth 5.3
    weight VARCHAR(100),       -- Ví dụ: 2.1 kg
    color VARCHAR(100),        -- Ví dụ: Đen nhám (Matte Black)
    os VARCHAR(255),           -- Ví dụ: Windows 11 Home
    condition_status VARCHAR(100), -- Ví dụ: Mới 100%, Nguyên seal
    warranty VARCHAR(100),     -- Ví dụ: 24 tháng chính hãng
    more_info TEXT,            -- Thông tin mô tả thêm
    product_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);
INSERT INTO product_details (product_id, cpu, ram, storage, gpu, screen, battery, audio, ports, wireless, weight, color, condition_status, warranty, more_info) VALUES
(1, 'Intel Core i5 13450HX (10 nhân, 12 luồng, tối đa 4.6GHz)', '16GB DDR5 4800MHz, 2 khe RAM, hỗ trợ nâng cấp', '512GB SSD NVMe, 1 slot, hỗ trợ mở rộng', 'NVIDIA GeForce RTX 3050 6GB GDDR6', '15.6" FHD IPS (1920x1080), 120Hz, 250 nits, 45% NTSC', '56Wh', '2 loa stereo, Dolby Audio', '2 USB-A 3.2 Gen 1, 1 USB-C 3.2 Gen 2, HDMI 2.0, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.1', '2.5kg', 'Đen', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13450HX<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.6" FHD IPS (1920x1080), 120Hz<br>-Đồ họa: NVIDIA GeForce RTX 3050 6GB'),
(2, 'Intel Core i5 13420H (8 nhân, 12 luồng, tối đa 4.8GHz)', '16GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 1 slot, hỗ trợ mở rộng', 'Nvidia GeForce RTX 4050 6GB GDDR6', '16" FHD+ (1920x1200) IPS, 165Hz, 300 nits, 72% NTSC', '65Wh', '2 loa, DTS:X Ultra', '1 USB-C 3.2 Gen 2, 2 USB-A 3.2 Gen 1, HDMI 2.1, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.2', '2.2kg', 'Xám', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13420H<br>-RAM: 16GB DDR5 5200MHz<br>-Màn hình: 16" FHD+ (1920x1200) IPS, 165Hz<br>-Đồ họa: Nvidia GeForce RTX 4050 6GB'),
(3, 'Intel Core i5 13420H (8 nhân, 12 luồng, tối đa 4.7GHz)', '16GB DDR5 4800MHz, 1 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 1 slot, hỗ trợ mở rộng', 'Intel UHD Graphics', '15.1" 2.5K (2560x1600) OLED, 165Hz, 500 nits, 100% sRGB', '45Wh', '2 loa, Dolby Audio', '1 USB-C 3.2 Gen 2, 2 USB-A 3.0, HDMI 1.4, jack 3.5mm', 'Wi-Fi 5, Bluetooth 5.0', '1.7kg', 'Xám bạc', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13420H<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.1", 2.5K (2560x1600) OLED, 500nits<br>-Đồ họa: Intel UHD Graphics'),
(4, 'Intel Core Ultra 5 225H (14 nhân, 18 luồng, tối đa 5.0GHz)', '32GB LPDDR5x 8533MHz, onboard', '1TB SSD NVMe, 1 slot', 'Intel Arc 130T GPU', '14.5" 3K (3072x1920), 120Hz, 500 nits, 100% DCI-P3', '60Wh', '2 loa, Dolby Atmos', '2 USB-C 4.0, 1 USB-A 3.2, HDMI 2.0, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.3', '1.4kg', 'Xanh dương', 'New 100%', '24 tháng tại Lenovo', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 5 225H<br>-RAM: 32GB LPDDR5x 8533MHz<br>-Màn hình: 14.5" 3K (3072x1920), ~500nits<br>-Đồ họa: Intel Arc 130T'),
(5, 'Intel Core i7 14650HX (14 nhân 24 luồng, tới 5.2GHZ)', '16GB RAM DDR5 5600MHZ hỗ trợ nâng cấp, 2 khe lắp ram', '1TB PCIe Gen5 SSD, hỗ trợ nâng cấp, 2 slot SSD', 'NVIDIA®GeForce® RTX™ 5060 8GB GDDR7', '16" 2.5K (2560x1600) IPS nhám, 500nits, 100% sRGB, 240Hz', '83Wh', '2 loa nằm ở 2 mé cạnh đáy trái phải', '2 cổng USB-A 3.2 Gen 1, 1 USB-A 3.2 Gen 2, 1 USB-C 3.2 Gen 2', 'Wi-Fi® 6E, Bluetooth 5.3', '~2.43kg', 'Đen', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 14650HX<br>-RAM: 16GB DDR5 5600MHz<br>-Màn hình: 16" 2.5K 240Hz<br>-Đồ họa: NVIDIA RTX 4060'),
(6, 'AMD Ryzen AI 9 365 (12 nhân, 24 luồng, tối đa 5.2GHz)', '32GB LPDDR5x 8000MHz, onboard', '1TB SSD NVMe, 1 slot', 'AMD Radeon 880M', '14" 2.8K (2880x1800) OLED, 120Hz, 400 nits, 100% sRGB', '70Wh', '2 loa, Dolby Atmos', '2 USB-C 4.0, 1 USB-A 3.2, HDMI 2.1, jack 3.5mm', 'Wi-Fi 7, Bluetooth 5.3', '1.5kg', 'Bạc', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen AI 9 365<br>-RAM: 32GB LPDDR5x 8000 onboard<br>-Màn hình: 14", 2.8K (2880x1800) OLED<br>-Đồ họa: AMD Radeon 880M'),
(7, 'AMD Ryzen 7 H 255 (8 nhân, 16 luồng, tối đa 4.9GHz)', '24GB DDR5 4800MHz, 2 khe, hỗ trợ nâng cấp', '1TB SSD NVMe, 1 slot', 'AMD Radeon 780M', '14" 2.8K (2880x1800) OLED, 120Hz, 400 nits, 100% sRGB', '55Wh', '2 loa, Dolby Audio', '1 USB-C, 2 USB-A 3.0, HDMI 1.4, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.2', '1.6kg', 'Xám', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen 7 H 255<br>-RAM: 24GB DDR5 4800MHz<br>-Màn hình: 14", 2.8K (2880x1800) OLED<br>-Đồ họa: AMD Radeon 780M'),
(8, 'Intel Core i9 14900HX (24 nhân, 32 luồng, tối đa 5.4GHz)', '16GB DDR5 5600MHz, 2 khe, hỗ trợ nâng cấp', '1TB SSD NVMe, 2 slots, hỗ trợ mở rộng', 'NVIDIA GeForce RTX 5060 8GB GDDR6', '16" 2.5K (2560x1600), 240Hz, 500 nits, 100% sRGB', '80Wh', '2 loa, Nahimic Audio', '2 USB-C 4.0, 2 USB-A 3.2, HDMI 2.1, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.3', '2.4kg', 'Đen', 'New 100%', '24 tháng tại Lenovo', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i9 14900HX<br>-RAM: 16GB DDR5 5600MHz<br>-Màn hình: 16" 2.5K (2560x1600), 240Hz<br>-Đồ họa: NVIDIA RTX 5060'),
(9, 'Intel Core Ultra 5 225H (14 nhân, 18 luồng, tối đa 5.0GHz)', '32GB LPDDR5x 8533MHz, onboard', '1TB SSD NVMe, 1 slot', 'Intel Arc 130T GPU', '16" 2.8K (2880x1920) OLED, 120Hz, 500 nits, 100% DCI-P3', '70Wh', '2 loa, Dolby Atmos', '2 USB-C 4.0, 1 USB-A 3.2, HDMI 2.0, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.3', '1.8kg', 'Xanh lá', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 5 225H<br>-RAM: 32GB LPDDR5x 8533 onboard<br>-Màn hình: 16", 2.8K (2880x1800) OLED<br>-Đồ họa: Intel Arc 130T'),
(10, 'Intel Core i5 13420H (8 nhân, 12 luồng, tối đa 4.8GHz)', '16GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 1 slot, hỗ trợ mở rộng', 'Nvidia GeForce RTX 4050 6GB GDDR6', '16" FHD+ (1920x1200) IPS, 165Hz, 300 nits, 72% NTSC', '65Wh', '2 loa, DTS:X Ultra', '1 USB-C 3.2 Gen 2, 2 USB-A 3.2 Gen 1, HDMI 2.1, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.2', '2.2kg', 'Xám', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13420H<br>-RAM: 16GB DDR5 5200MHz<br>-Màn hình: 16" FHD+ (1920x1200) IPS, 165Hz<br>-Đồ họa: Nvidia GeForce RTX 4050 6GB'),
(11, 'Intel Core i7 14650HX (16 nhân, 24 luồng, tối đa 5.2GHz)', '16GB DDR5 5600MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slots, hỗ trợ mở rộng', 'NVIDIA GeForce RTX 5060 8GB GDDR6', '15.3" 2.5K (2560x1600), 180Hz, 400 nits, 100% sRGB', '75Wh', '2 loa, Nahimic Audio', '1 USB-C 4.0, 2 USB-A 3.2, HDMI 2.1, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.3', '2.3kg', 'Xám', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 14650HX<br>-RAM: 16GB DDR5 5600MHz<br>-Màn hình: 15.3" 2.5K (2560x1600), 180Hz<br>-Đồ họa: NVIDIA RTX 5060'),
(12, 'AMD Ryzen 9 8945HX (12 nhân, 24 luồng, tối đa 5.4GHz)', '32GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '1TB SSD NVMe, 2 slots, hỗ trợ mở rộng', 'NVIDIA GeForce RTX 5060 8GB GDDR6', '16" 2.5K (2560x1600), 240Hz, 500 nits, 100% sRGB', '80Wh', '2 loa, Nahimic Audio', '2 USB-C 4.0, 2 USB-A 3.2, HDMI 2.1, jack 3.5mm', 'Wi-Fi 7, Bluetooth 5.3', '2.5kg', 'Đen', 'New 100%', '24 tháng tại Lenovo', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen 9 8945HX<br>-RAM: 32GB DDR5 5200MHz<br>-Màn hình: 16" (2560x1600), 240Hz, 500nits<br>-Đồ họa: NVIDIA RTX 5060'),
(13, 'Intel Core i5 13420H (8 nhân, 12 luồng, tối đa 4.7GHz)', '16GB DDR5 4800MHz, 1 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 1 slot, hỗ trợ mở rộng', 'Intel UHD Graphics', '15.1" 2.5K (2560x1600) OLED, 165Hz, 500 nits, 100% sRGB', '45Wh', '2 loa, Dolby Audio', '1 USB-C, 2 USB-A 3.0, HDMI 1.4, jack 3.5mm', 'Wi-Fi 5, Bluetooth 5.0', '1.7kg', 'Xám bạc', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13420H<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.1", 2.5K (2560x1600) OLED, 500nits<br>-Đồ họa: Intel UHD Graphics'),
(14, 'Intel Core Ultra 5 225H (14 nhân, 18 luồng, tối đa 5.0GHz)', '32GB LPDDR5x 8533MHz, onboard', '1TB SSD NVMe, 1 slot', 'Intel Arc 130T GPU', '14.5" 3K (3072x1920), 120Hz, 500 nits, 100% DCI-P3', '60Wh', '2 loa, Dolby Atmos', '2 USB-C 4.0, 1 USB-A 3.2, HDMI 2.0, jack 3.5mm', 'Wi-Fi 6E, Bluetooth 5.3', '1.4kg', 'Xanh dương', 'New 100%', '36 tháng tại Lenovo', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 5 225H<br>-RAM: 32GB LPDDR5x 8533MHz<br>-Màn hình: 14.5" 3K (3072x1920), ~500nits<br>-Đồ họa: Intel Arc 130T'),
(15, 'Apple M2 Pro 10 core (8 hiệu năng, 2 tiết kiệm)', '16GB unified memory, onboard', '512GB SSD, không hỗ trợ mở rộng', 'Apple M2 GPU 16 core', '14.2" Liquid Retina XDR (3024x1964), 120Hz, 1000 nits', '70Wh', '6 loa, Spatial Audio', '3 Thunderbolt 4, HDMI, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.0', '1.6kg', 'Xám không gian', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: Apple M2 Pro 10 core<br>-RAM: 16GB<br>-Màn hình: 14.2" 3K Liquid Retina XDR (3024x1964)<br>-Đồ họa: Apple M2 GPU 16 core'),
(16, 'Apple M1 Pro 10 core (8 hiệu năng, 2 tiết kiệm)', '16GB unified memory, onboard', '512GB SSD, không hỗ trợ mở rộng', 'Apple M1 Pro GPU 16 core', '16.2" Liquid Retina XDR (3456x2234), 120Hz, 1000 nits', '75Wh', '6 loa, Spatial Audio', '3 Thunderbolt 4, HDMI, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.0', '2.1kg', 'Bạc', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: M1 Pro 10 core<br>-RAM: 16GB<br>-Màn hình: 16.2" Liquid Retina XDR (3456x2234)<br>-Đồ họa: Apple M1 Pro GPU 16 core'),
(17, 'Apple M1 Pro 10 core (8 hiệu năng, 2 tiết kiệm)', '16GB unified memory, onboard', '512GB SSD, không hỗ trợ mở rộng', 'Apple M1 Pro GPU 16 core', '14" Liquid Retina XDR (3024x1964), 120Hz, 1000 nits', '70Wh', '6 loa, Spatial Audio', '3 Thunderbolt 4, HDMI, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.0', '1.6kg', 'Xám không gian', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: M1 Pro 10 core<br>-RAM: 16GB<br>-Màn hình: Liquid Retina XDR (3024x1964)<br>-Đồ họa: Apple M1 Pro GPU 16 core'),
(18, 'Apple M1 Pro 8 core (6 hiệu năng, 2 tiết kiệm)', '16GB unified memory, onboard', '512GB SSD, không hỗ trợ mở rộng', 'Apple M1 Pro GPU 14 core', '14" Liquid Retina XDR (3024x1964), 120Hz, 1000 nits', '70Wh', '6 loa, Spatial Audio', '3 Thunderbolt 4, HDMI, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.0', '1.6kg', 'Bạc', 'Like New 99%', '3 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: M1 Pro 8 core<br>-RAM: 16GB<br>-Màn hình: Liquid Retina XDR (3024x1964)<br>-Đồ họa: Apple M1 Pro GPU 14 core'),
(19, 'Apple M1 Pro 10 core (8 hiệu năng, 2 tiết kiệm)', '16GB unified memory, onboard', '512GB SSD, không hỗ trợ mở rộng', 'Apple M1 Pro GPU 16 core', '16.2" Liquid Retina XDR (3456x2234), 120Hz, 1000 nits', '75Wh', '6 loa, Spatial Audio', '3 Thunderbolt 4, HDMI, jack 3.5mm', 'Wi-Fi 6, Bluetooth 5.0', '2.1kg', 'Bạc', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: M1 Pro 10 core<br>-RAM: 16GB<br>-Màn hình: 16.2" Liquid Retina XDR (3456x2234)<br>-Đồ họa: Apple M1 Pro GPU 16 core'),
(20, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'cấu hình siêu khủng, cân hết mọi tựa game'),
(21, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Cấu hình siêu khủng, phù hợp cho việc làm đồ họa 24/24'),
(22, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'abcbcsds');


-- 5. Bảng cart_items
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quantity INT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);




-- 6. Bảng orders
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    total_amount DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL,
    order_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    customer_name VARCHAR(100),
    customer_phone VARCHAR(20),
    address TEXT,
    user_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 7. Bảng order_details
CREATE TABLE order_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quantity INT NOT NULL,
    price DOUBLE NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);



-- bảng ảnh chi tiết sản phẩm
CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_url VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(id)
);
INSERT INTO product_images (product_id, image_url) VALUES 
(1, 'assets/Dell_gaming_G15_5530_img0.jpg'), (1, 'assets/Dell_gaming_G15_5530_img1.jpg'), (1, 'assets/Dell_gaming_G15_5530_img2.jpg'), (1, 'assets/Dell_gaming_G15_5530_img3.jpg'), (1, 'assets/Dell_gaming_G15_5530_img4.jpg'),
(2, 'assets/gigabyte_gaming_a16_img0.png'), (2, 'assets/gigabyte_gaming_a16_img1.png'), (2, 'assets/gigabyte_gaming_a16_img2.png'), (2, 'assets/gigabyte_gaming_a16_img3.png'), (2, 'assets/gigabyte_gaming_a16_img4.png'),
(3, 'assets/ideapad_slim3_15irh10_img0.png'), (3, 'assets/ideapad_slim3_15irh10_img1.png'), (3, 'assets/ideapad_slim3_15irh10_img2.png'), (3, 'assets/ideapad_slim3_15irh10_img3.png'), (3, 'assets/ideapad_slim3_15irh10_img4.png'),
(4, 'assets/thinkbook_14_g7_img0.jpg'), (4, 'assets/thinkbook_14_g7_img1.jpg'), (4, 'assets/thinkbook_14_g7_img2.jpg'), (4, 'assets/thinkbook_14_g7_img3.jpg'), (4, 'assets/thinkbook_14_g7_img4.jpg'),
(5, 'assets/acer_sadow_desc_img0.png'), (5, 'assets/acer_shadow_desc_img2.png'), (5, 'assets/acer_shadow_desc_img3.png'), (5, 'assets/acer_sadow_desc_img4.png'), (5, 'assets/acer_sadow_desc_img5.png'),
(6, 'assets/ideapad_5pro_14gt_img0.jpg'), (6, 'assets/ideapad_5pro_14gt_img1.jpg'), (6, 'assets/ideapad_5pro_14gt_img2.jpg'), (6, 'assets/ideapad_5pro_14gt_img3.jpg'), (6, 'assets/ideapad_5pro_14gt_img4.jpg'),
(7, 'assets/ideapad_slim5_2025_img0.png'), (7, 'assets/ideapad_slim5_2025_img1.png'), (7, 'assets/ideapad_slim5_2025_img2.png'), (7, 'assets/ideapad_slim5_2025_img3.png'), (7, 'assets/ideapad_slim5_2025_img4.png'),
(8, 'assets/y7000_2025_img0.jpg'), (8, 'assets/y7000_2025_img1.jpg'), (8, 'assets/y7000_2025_img2.jpg'), (8, 'assets/y7000_2025_img3.jpg'), (8, 'assets/y7000_2025_img4.jpg'),
(9, 'assets/ideapad_5pro_16gt_img0.jpg'), (9, 'assets/ideapad_5pro_16gt_img1.jpg'), (9, 'assets/ideapad_5pro_16gt_img2.jpg'), (9, 'assets/ideapad_5pro_16gt_img3.jpg'), (9, 'assets/ideapad_5pro_16gt_img4.jpg'),
(10, 'assets/gigabyte_gaming_a16_img0.png'), (10, 'assets/gigabyte_gaming_a16_img1.png'), (10, 'assets/gigabyte_gaming_a16_img2.png'), (10, 'assets/gigabyte_gaming_a16_img3.png'), (10, 'assets/gigabyte_gaming_a16_img4.png'),
(11, 'assets/y7000_2025_img0.jpg'), (11, 'assets/y7000_2025_img1.jpg'), (11, 'assets/y7000_2025_img2.jpg'), (11, 'assets/y7000_2025_img3.jpg'), (11, 'assets/y7000_2025_img4.jpg'),
(12, 'assets/legion_pro5_r9000p_img0.jpg'), (12, 'assets/legion_pro5_r9000p_img1.jpg'), (12, 'assets/legion_pro5_r9000p_img2.jpg'), (12, 'assets/legion_pro5_r9000p_img3.jpg'), (12, 'assets/legion_pro5_r9000p_img4.jpg'),
(13, 'assets/ideapad_slim3_15irh10_img0.png'), (13, 'assets/ideapad_slim3_15irh10_img1.png'), (13, 'assets/ideapad_slim3_15irh10_img2.png'), (13, 'assets/ideapad_slim3_15irh10_img3.png'), (13, 'assets/ideapad_slim3_15irh10_img4.png'),
(14, 'assets/thinkbook_14_g7_img0.jpg'), (14, 'assets/thinkbook_14_g7_img1.jpg'), (14, 'assets/thinkbook_14_g7_img2.jpg'), (14, 'assets/thinkbook_14_g7_img3.jpg'), (14, 'assets/thinkbook_14_g7_img4.jpg'),
(15, 'assets/macbook_pro14_M2_img0.jpg'), (15, 'assets/macbook_pro14_M2_img1.jpg'), (15, 'assets/macbook_pro14_M2_img2.jpg'), (15, 'assets/macbook_pro14_M2_img3.jpg'), (15, 'assets/macbook_pro14_M2_img4.jpg'),
(16, 'assets/macbook_pro_16m1_img0.jpg'), (16, 'assets/macbook_pro_16m1_img1.jpg'), (16, 'assets/macbook_pro_16m1_img2.jpg'), (16, 'assets/macbook_pro_16m1_img3.jpg'), (16, 'assets/macbook_pro_16m1_img4.jpg'),
(17, 'assets/macbook_pro_14_m1_img0.png'), (17, 'assets/macbook_pro_14_m1_img1.png'), (17, 'assets/macbook_pro_14_m1_img2.png'), (17, 'assets/macbook_pro_14_m1_img3.png'), (17, 'assets/macbook_pro_14_m1_img4.png'),
(18, 'assets/macbook_pro_14_m1_img0.png'), (18, 'assets/macbook_pro_14_m1_img1.png'), (18, 'assets/macbook_pro_14_m1_img2.png'), (18, 'assets/macbook_pro_14_m1_img3.png'), (18, 'assets/macbook_pro_14_m1_img4.png'),
(19, 'assets/macbook_pro_16m1_img0.jpg'), (19, 'assets/macbook_pro_16m1_img1.jpg'), (19, 'assets/macbook_pro_16m1_img2.jpg'), (19, 'assets/macbook_pro_16m1_img3.jpg'), (19, 'assets/macbook_pro_16m1_img4.jpg'),
(20, 'assets/idea_padpro_14gt.jpg'), (21, 'assets/dohoakythuat.webp'), (22, 'assets/acer_sadow_desc_img0.png');

-- Dữ liệu thêm về sản phẩm
-- 1. Bảng products
INSERT INTO products (id, name, price, stock, image, category_id) VALUES 
(101, 'Acer Predator Helios Neo 16 (2024)', 32990000, 15, 'assets/acer_helios_neo_16_img0.jpg', 1),
(102, 'Acer Nitro V 15 (2024)', 24590000, 20, 'assets/acer_nitro_v15_img0.jpg', 1),
(103, 'Acer Swift Go 14 OLED (2024)', 19990000, 10, 'assets/acer_swift_go14_img0.jpg', 1),

(104, 'Asus ROG Zephyrus G14 (2024)', 39990000, 8, 'assets/asus_rog_g14_img0.jpg', 4),
(105, 'Asus TUF Gaming A15 (2024)', 26490000, 12, 'assets/asus_tuf_a15_img0.jpg', 4),
(106, 'Asus Zenbook S 13 OLED (2024)', 29990000, 5, 'assets/asus_zenbook_s13_img0.jpg', 4),

(107, 'Dell XPS 15 9530 (2024)', 45990000, 5, 'assets/dell_xps_15_img0.jpg', 5),
(108, 'Dell Inspiron 16 Plus 7630 (2024)', 28990000, 8, 'assets/dell_inspiron_16_img0.jpg', 5),
(109, 'Dell Alienware m16 R2 (2024)', 52990000, 3, 'assets/dell_alienware_m16_img0.jpg', 5),

(110, 'Lenovo Legion Slim 5 16IRH8 (2024)', 29990000, 12, 'assets/lenovo_legion_slim5_img0.jpg', 7),
(111, 'Lenovo LOQ 15IRX9 (2024)', 23990000, 25, 'assets/lenovo_loq_15_img0.jpg', 7),
(112, 'Lenovo ThinkPad T14 Gen 4 (2024)', 34500000, 10, 'assets/lenovo_thinkpad_t14_img0.jpg', 7),

(113, 'HP Omen 16 (2024)', 31500000, 7, 'assets/hp_omen_16_img0.jpg', 6),
(114, 'HP Victus 16 (2024)', 22990000, 15, 'assets/hp_victus_16_img0.jpg', 6),
(115, 'HP Envy x360 14 (2024)', 25990000, 10, 'assets/hp_envy_x360_img0.jpg', 6),

(116, 'MacBook Air 15 inch M3 (2024)', 32990000, 20, 'assets/macbook_air_15_m3_img0.jpg', 3),
(117, 'MacBook Air 13 inch M3 (2024)', 27990000, 30, 'assets/macbook_air_13_m3_img0.jpg', 3),
(118, 'MacBook Pro 16 inch M3 Max (2024)', 89990000, 5, 'assets/macbook_pro_16_m3max_img0.jpg', 3);


-- 2. Bảng product_details
INSERT INTO product_details (product_id, cpu, ram, storage, gpu, screen, battery, audio, ports, wireless, weight, color, condition_status, warranty, more_info) VALUES 
(101, 'Intel Core i7 13700HX (16 nhân, 24 luồng, tối đa 5.0GHz)', '16GB DDR5 4800MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4060 8GB GDDR6', '16" WQXGA (2560x1600) 165Hz, 500 nits, 100% sRGB', '90Wh', 'DTS:X Ultra', '2x USB-C 3.2, 3x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.2', '2.6kg', 'Đen', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 13700HX<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 16" 2.5K 165Hz<br>-Đồ họa: NVIDIA RTX 4060 8GB<br>-Nhu cầu: Gaming / Đồ họa'),
(102, 'Intel Core i5 13420H (8 nhân, 12 luồng, tối đa 4.6GHz)', '16GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4050 6GB GDDR6', '15.6" FHD (1920x1080) 144Hz, 250 nits', '57Wh', 'DTS:X Ultra', '1x USB-C 3.2, 3x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6, Bluetooth 5.1', '2.11kg', 'Đen', 'New 100%', '12 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13420H<br>-RAM: 16GB DDR5 5200MHz<br>-Màn hình: 15.6" FHD 144Hz<br>-Đồ họa: NVIDIA RTX 4050 6GB<br>-Nhu cầu: Gaming / Học tập'),
(103, 'Intel Core Ultra 5 125H (14 nhân, 18 luồng)', '16GB LPDDR5X 6400MHz, onboard', '512GB SSD NVMe, 1 slot', 'Intel Arc Graphics', '14" 2.8K OLED (2880x1800) 90Hz, 400 nits, 100% DCI-P3', '65Wh', 'Acer TrueHarmony', '2x Thunderbolt 4, 2x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.3', '1.32kg', 'Bạc', 'New 100%', '12 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 5 125H<br>-RAM: 16GB LPDDR5X<br>-Màn hình: 14" 2.8K OLED 90Hz<br>-Đồ họa: Intel Arc Graphics<br>-Nhu cầu: Văn phòng / Lập trình nhẹ'),

(104, 'AMD Ryzen 9 7940HS (8 nhân, 16 luồng, tối đa 5.2GHz)', '16GB DDR5 4800MHz, onboard', '1TB SSD NVMe, 1 slot', 'NVIDIA GeForce RTX 4060 8GB GDDR6', '14" QHD+ (2560x1600) 165Hz, 500 nits, 100% DCI-P3', '76Wh', '4 loa, Dolby Atmos', '2x USB-C 3.2, 2x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.3', '1.65kg', 'Xám', 'New 100%', '24 tháng tại hãng', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen 9 7940HS<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 14" QHD+ 165Hz<br>-Đồ họa: NVIDIA RTX 4060 8GB<br>-Nhu cầu: Gaming / Lập trình'),
(105, 'AMD Ryzen 7 7735HS (8 nhân, 16 luồng, tối đa 4.7GHz)', '16GB DDR5 4800MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4050 6GB GDDR6', '15.6" FHD (1920x1080) 144Hz, 100% sRGB', '90Wh', 'Dolby Atmos', '1x USB-C 4.0, 1x USB-C 3.2, 2x USB-A 3.2', 'Wi-Fi 6, Bluetooth 5.2', '2.2kg', 'Xám đen', 'New 100%', '24 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen 7 7735HS<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.6" FHD 144Hz<br>-Đồ họa: NVIDIA RTX 4050 6GB<br>-Nhu cầu: Gaming / Học tập'),
(106, 'Intel Core i7 1355U (10 nhân, 12 luồng, tối đa 5.0GHz)', '16GB LPDDR5 5200MHz, onboard', '1TB SSD NVMe', 'Intel Iris Xe Graphics', '13.3" 2.8K OLED (2880x1800) 60Hz, 550 nits', '63Wh', 'Harman Kardon, Dolby Atmos', '2x Thunderbolt 4, 1x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.3', '1.0kg', 'Xanh đen', 'New 100%', '24 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 1355U<br>-RAM: 16GB LPDDR5<br>-Màn hình: 13.3" 2.8K OLED<br>-Đồ họa: Intel Iris Xe<br>-Nhu cầu: Văn phòng / Lập trình'),

(107, 'Intel Core i7 13700H (14 nhân, 20 luồng, tối đa 5.0GHz)', '16GB DDR5 4800MHz, 2 khe, hỗ trợ nâng cấp', '1TB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4050 6GB GDDR6', '15.6" FHD+ (1920x1200), 500 nits, 100% sRGB', '86Wh', 'Studio Quality, Waves Nx 3D', '2x Thunderbolt 4, 1x USB-C 3.2', 'Wi-Fi 6, Bluetooth 5.3', '1.92kg', 'Bạc', 'New 100%', '12 tháng tại D-computer', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 13700H<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.6" FHD+ 500 nits<br>-Đồ họa: NVIDIA RTX 4050 6GB<br>-Nhu cầu: Đồ họa / Lập trình'),
(108, 'Intel Core i7 13700H (14 nhân, 20 luồng)', '16GB LPDDR5 4800MHz, onboard', '1TB SSD NVMe', 'NVIDIA GeForce RTX 3050 6GB GDDR6', '16.0" 2.5K (2560x1600) 120Hz, 300 nits, 100% sRGB', '86Wh', 'Waves MaxxAudio Pro', '1x Thunderbolt 4, 2x USB-A 3.2, HDMI 2.0', 'Wi-Fi 6E, Bluetooth 5.2', '2.06kg', 'Bạc', 'New 100%', '12 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 13700H<br>-RAM: 16GB LPDDR5<br>-Màn hình: 16" 2.5K 120Hz<br>-Đồ họa: NVIDIA RTX 3050 6GB<br>-Nhu cầu: Văn phòng / Lập trình'),
(109, 'Intel Core Ultra 7 155H (16 nhân, 22 luồng)', '16GB DDR5 5600MHz, 2 khe, hỗ trợ nâng cấp', '1TB SSD NVMe', 'NVIDIA GeForce RTX 4070 8GB GDDR6', '16.0" QHD+ (2560x1600) 240Hz, 100% sRGB', '90Wh', 'Alienware Sound', '2x Thunderbolt 4, 2x USB-A 3.2, HDMI 2.1, RJ45', 'Wi-Fi 7, Bluetooth 5.4', '2.61kg', 'Đen ánh kim', 'New 100%', '12 tháng cao cấp', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 7 155H<br>-RAM: 16GB DDR5 5600MHz<br>-Màn hình: 16" QHD+ 240Hz<br>-Đồ họa: NVIDIA RTX 4070 8GB<br>-Nhu cầu: Gaming / Đồ họa'),

(110, 'Intel Core i7 13700H (14 nhân, 20 luồng, tối đa 5.0GHz)', '16GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4060 8GB GDDR6', '16" WQXGA (2560x1600) 165Hz, 300 nits, 100% sRGB', '80Wh', '2 loa, Nahimic Audio', '2x USB-C 3.2, 2x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.1', '2.4kg', 'Xám', 'New 100%', '24 tháng tại Lenovo', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 13700H<br>-RAM: 16GB DDR5 5200MHz<br>-Màn hình: 16" 2.5K 165Hz<br>-Đồ họa: NVIDIA RTX 4060 8GB<br>-Nhu cầu: Gaming / Lập trình'),
(111, 'Intel Core i7 13650HX (14 nhân, 20 luồng)', '16GB DDR5 4800MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe', 'NVIDIA GeForce RTX 4050 6GB GDDR6', '15.6" FHD (1920x1080) 144Hz, 100% sRGB', '60Wh', 'Nahimic Audio', '1x USB-C 3.2, 3x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6, Bluetooth 5.1', '2.38kg', 'Xám', 'New 100%', '24 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i7 13650HX<br>-RAM: 16GB DDR5 4800MHz<br>-Màn hình: 15.6" FHD 144Hz<br>-Đồ họa: NVIDIA RTX 4050 6GB<br>-Nhu cầu: Gaming / Học tập'),
(112, 'Intel Core i5 1335U (10 nhân, 12 luồng)', '16GB LPDDR5x 4800MHz, onboard', '512GB SSD NVMe', 'Intel Iris Xe Graphics', '14.0" WUXGA (1920x1200) IPS, 300 nits, chống chói', '52.5Wh', 'Dolby Audio', '2x Thunderbolt 4, 2x USB-A 3.2, HDMI 2.1, RJ45', 'Wi-Fi 6E, Bluetooth 5.3', '1.36kg', 'Đen', 'New 100%', '36 tháng Premier Support', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 1335U<br>-RAM: 16GB LPDDR5x<br>-Màn hình: 14" WUXGA IPS<br>-Đồ họa: Intel Iris Xe<br>-Nhu cầu: Lập trình / Văn phòng'),

(113, 'AMD Ryzen 7 7840HS (8 nhân, 16 luồng, tối đa 5.1GHz)', '16GB DDR5 5600MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe, 2 slot', 'NVIDIA GeForce RTX 4060 8GB GDDR6', '16.1" FHD (1920x1080) 165Hz, 300 nits, 100% sRGB', '83Wh', 'Bang & Olufsen, DTS:X', '2x USB-C 3.2, 2x USB-A 3.2, HDMI 2.1', 'Wi-Fi 6E, Bluetooth 5.3', '2.37kg', 'Đen', 'New 100%', '12 tháng tại hãng', 'Chi Tiết Cấu Hình<br>-CPU: AMD Ryzen 7 7840HS<br>-RAM: 16GB DDR5 5600MHz<br>-Màn hình: 16.1" FHD 165Hz<br>-Đồ họa: NVIDIA RTX 4060 8GB<br>-Nhu cầu: Gaming / Đồ họa'),
(114, 'Intel Core i5 13500H (12 nhân, 16 luồng)', '16GB DDR5 5200MHz, 2 khe, hỗ trợ nâng cấp', '512GB SSD NVMe', 'NVIDIA GeForce RTX 4050 6GB GDDR6', '16.1" FHD (1920x1080) 144Hz, 250 nits', '70Wh', 'B&O Audio', '1x USB-C 3.2, 3x USB-A 3.2, HDMI 2.1, RJ45', 'Wi-Fi 6E, Bluetooth 5.3', '2.33kg', 'Xanh đen', 'New 100%', '12 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core i5 13500H<br>-RAM: 16GB DDR5 5200MHz<br>-Màn hình: 16.1" FHD 144Hz<br>-Đồ họa: NVIDIA RTX 4050 6GB<br>-Nhu cầu: Gaming / Học tập'),
(115, 'Intel Core Ultra 7 155U (12 nhân, 14 luồng)', '16GB LPDDR5 6400MHz, onboard', '1TB SSD NVMe', 'Intel Graphics', '14.0" WUXGA (1920x1200) IPS Cảm ứng, 300 nits', '59Wh', 'Poly Studio, DTS:X', '2x Thunderbolt 4, 2x USB-A 3.2, HDMI 2.1', 'Wi-Fi 7, Bluetooth 5.4', '1.39kg', 'Bạc', 'New 100%', '12 tháng chính hãng', 'Chi Tiết Cấu Hình<br>-CPU: Intel Core Ultra 7 155U<br>-RAM: 16GB LPDDR5 6400MHz<br>-Màn hình: 14" WUXGA Cảm ứng<br>-Đồ họa: Intel Graphics<br>-Nhu cầu: Văn phòng / Doanh nhân'),

(116, 'Apple M3 (8-core CPU)', '16GB Unified Memory, onboard', '512GB SSD, không hỗ trợ nâng cấp', '10-core GPU', '15.3" Liquid Retina (2880x1864), 500 nits', '66.5Wh', '6 loa, Spatial Audio', '2x Thunderbolt / USB 4, MagSafe 3', 'Wi-Fi 6E, Bluetooth 5.3', '1.51kg', 'Starlight', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: Apple M3 8-core<br>-RAM: 16GB Unified<br>-Màn hình: 15.3" Liquid Retina<br>-Đồ họa: 10-core GPU<br>-Nhu cầu: Văn phòng / Lập trình'),
(117, 'Apple M3 (8-core CPU)', '8GB Unified Memory, onboard', '256GB SSD, không hỗ trợ nâng cấp', '8-core GPU', '13.6" Liquid Retina (2560x1664), 500 nits', '52.6Wh', '4 loa, Spatial Audio', '2x Thunderbolt / USB 4, MagSafe 3', 'Wi-Fi 6E, Bluetooth 5.3', '1.24kg', 'Midnight', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: Apple M3 8-core<br>-RAM: 8GB Unified<br>-Màn hình: 13.6" Liquid Retina<br>-Đồ họa: 8-core GPU<br>-Nhu cầu: Văn phòng / Học tập'),
(118, 'Apple M3 Max (16-core CPU)', '48GB Unified Memory, onboard', '1TB SSD, không hỗ trợ nâng cấp', '40-core GPU', '16.2" Liquid Retina XDR (3456x2234) 120Hz, 1000 nits', '100Wh', '6 loa, Spatial Audio', '3x Thunderbolt 4, HDMI, SDXC, MagSafe 3', 'Wi-Fi 6E, Bluetooth 5.3', '2.16kg', 'Space Black', 'New 100%', '12 tháng tại Apple', 'Chi Tiết Cấu Hình<br>-CPU: Apple M3 Max 16-core<br>-RAM: 48GB Unified<br>-Màn hình: 16.2" Liquid Retina XDR 120Hz<br>-Đồ họa: 40-core GPU<br>-Nhu cầu: Đồ họa / Lập trình nặng');


-- 3. Bảng product_images (Thêm 3 ảnh cho mỗi sản phẩm)
INSERT INTO product_images (product_id, image_url) VALUES 
(101, 'assets/acer_helios_neo_16_img0.jpg'), (101, 'assets/acer_helios_neo_16_img1.jpg'), (101, 'assets/acer_helios_neo_16_img2.jpg'), 
(102, 'assets/acer_nitro_v15_img0.jpg'), (102, 'assets/acer_nitro_v15_img1.jpg'), (102, 'assets/acer_nitro_v15_img2.jpg'), 
(103, 'assets/acer_swift_go14_img0.jpg'), (103, 'assets/acer_swift_go14_img1.jpg'), (103, 'assets/acer_swift_go14_img2.jpg'), 

(104, 'assets/asus_rog_g14_img0.jpg'), (104, 'assets/asus_rog_g14_img1.jpg'), (104, 'assets/asus_rog_g14_img2.jpg'), 
(105, 'assets/asus_tuf_a15_img0.jpg'), (105, 'assets/asus_tuf_a15_img1.jpg'), (105, 'assets/asus_tuf_a15_img2.jpg'), 
(106, 'assets/asus_zenbook_s13_img0.jpg'), (106, 'assets/asus_zenbook_s13_img1.jpg'), (106, 'assets/asus_zenbook_s13_img2.jpg'), 

(107, 'assets/dell_xps_15_img0.jpg'), (107, 'assets/dell_xps_15_img1.jpg'), (107, 'assets/dell_xps_15_img2.jpg'), 
(108, 'assets/dell_inspiron_16_img0.jpg'), (108, 'assets/dell_inspiron_16_img1.jpg'), (108, 'assets/dell_inspiron_16_img2.jpg'), 
(109, 'assets/dell_alienware_m16_img0.jpg'), (109, 'assets/dell_alienware_m16_img1.jpg'), (109, 'assets/dell_alienware_m16_img2.jpg'), 

(110, 'assets/lenovo_legion_slim5_img0.jpg'), (110, 'assets/lenovo_legion_slim5_img1.jpg'), (110, 'assets/lenovo_legion_slim5_img2.jpg'), 
(111, 'assets/lenovo_loq_15_img0.jpg'), (111, 'assets/lenovo_loq_15_img1.jpg'), (111, 'assets/lenovo_loq_15_img2.jpg'), 
(112, 'assets/lenovo_thinkpad_t14_img0.jpg'), (112, 'assets/lenovo_thinkpad_t14_img1.jpg'), (112, 'assets/lenovo_thinkpad_t14_img2.jpg'), 

(113, 'assets/hp_omen_16_img0.jpg'), (113, 'assets/hp_omen_16_img1.jpg'), (113, 'assets/hp_omen_16_img2.jpg'), 
(114, 'assets/hp_victus_16_img0.jpg'), (114, 'assets/hp_victus_16_img1.jpg'), (114, 'assets/hp_victus_16_img2.jpg'), 
(115, 'assets/hp_envy_x360_img0.jpg'), (115, 'assets/hp_envy_x360_img1.jpg'), (115, 'assets/hp_envy_x360_img2.jpg'), 

(116, 'assets/macbook_air_15_m3_img0.jpg'), (116, 'assets/macbook_air_15_m3_img1.jpg'), (116, 'assets/macbook_air_15_m3_img2.jpg'),
(117, 'assets/macbook_air_13_m3_img0.jpg'), (117, 'assets/macbook_air_13_m3_img1.jpg'), (117, 'assets/macbook_air_13_m3_img2.jpg'),
(118, 'assets/macbook_pro_16_m3max_img0.jpg'), (118, 'assets/macbook_pro_16_m3max_img1.jpg'), (118, 'assets/macbook_pro_16_m3max_img2.jpg');
