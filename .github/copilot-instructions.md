# Quy tắc làm việc cho dự án Lapzone
- Stack: Java 25, Spring Boot 4.x, Thymeleaf, MySQL, Spring Data JPA, Maven.

## CẤM TUYỆT ĐỐI (ưu tiên cao nhất)
- KHÔNG sửa, đổi tên, xóa hay "làm sạch" các dòng spring.datasource.url / username / password trong application.properties (và mọi file cấu hình khác). Người dùng cần chạy lại web trên máy với đúng cấu hình hiện tại.
- KHÔNG đổi tên database, KHÔNG tạo hay xóa tài khoản MySQL, KHÔNG chạy lệnh SQL nào lên database thật.
- KHÔNG đổi spring.jpa.hibernate.ddl-auto. Nếu thấy giá trị nguy hiểm (create, create-drop), chỉ báo cho người dùng chứ không tự sửa.
- KHÔNG chạy lại lapzone_init_official.sql, insert_products.sql, lapzone_generate_bigdata.sql và không sửa chúng làm mất dữ liệu hiện có.
- Test KHÔNG được ghi, xóa hay làm bẩn dữ liệu trong database thật: dùng Mockito, @WebMvcTest, hoặc @Transactional có rollback.
- Cần thêm bảng/cột: đưa script SQL (ALTER/CREATE) để người dùng tự chạy, không tự chạy, và code mới phải không làm hỏng khi dữ liệu cũ chưa có cột đó nếu có thể.

## Quy tắc chung
- Chỉ sửa đúng phạm vi được yêu cầu. Không refactor lan man, không đổi tên package/class không liên quan.
- Trước khi sửa: đọc code liên quan và xác nhận vấn đề có thật. Nếu không có thật, nói rõ và dừng.
- Trình bày kế hoạch ngắn (sửa file nào, vì sao) rồi mới sửa.
- Thay đổi logic phải có test. Chạy `mvnw.cmd test` và báo kết quả thật, không nói "đã chạy" nếu chưa chạy.
- Đảm bảo ứng dụng vẫn khởi động được với cấu hình hiện có sau khi sửa.
- Cuối mỗi lượt: liệt kê file đã đổi, cách tự kiểm tra bằng tay trên web, rủi ro còn lại, và gợi ý commit message.