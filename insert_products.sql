-- =======================================================================
-- SCRIPT THÊM 18 SẢN PHẨM MỚI (Chia đều 3 sản phẩm/hãng)
-- ID bắt đầu từ 101 để không trùng với 22 sản phẩm bạn đang có sẵn.
-- Hãng: Acer(1), Asus(4), Dell(5), Lenovo(7), HP(6), Apple(3)
-- =======================================================================

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

-- 4. Seed users
-- Password for both accounts: Abc123@
INSERT INTO users (full_name, email, phone, password, address, role) VALUES
('User Seed', 'taobattu@gmail.com', '0900000001', '$2a$10$iXlZZiuvjLbNnfnbd7MBr.Ree1WyT7l6V7tiAwKyf0iMcFf234kHm', '', 'USER'),
('Admin Seed', 'Admin@gmail.com', '0900000002', '$2a$10$iXlZZiuvjLbNnfnbd7MBr.Ree1WyT7l6V7tiAwKyf0iMcFf234kHm', '', 'ADMIN')
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    phone = VALUES(phone),
    password = VALUES(password),
    address = VALUES(address),
    role = VALUES(role);
