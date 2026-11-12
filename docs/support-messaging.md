# Hỗ trợ khách hàng

- Khách hàng: `/support`, mở từ mục **Tin nhắn** trong `/store`.
- Quản trị viên: `/admin/support`, mở từ thanh điều hướng quản trị.
- Tạo yêu cầu: `POST /support` (`subject`, `content`).
- Lịch sử: `GET /support/{id}` hoặc `/admin/support/{id}`; danh sách 20 hội thoại/trang, lịch sử 50 tin/trang. Trang lịch sử 0 chứa các tin mới nhất, hiển thị theo thứ tự thời gian.
- Gửi tin: `POST /support/{id}/messages` hoặc `/admin/support/{id}/messages` (`content`).
- Đánh dấu đã đọc: `POST .../{id}/read` (`throughId`, `page`). Chỉ cập nhật tin từ phía đối diện có mã không lớn hơn tin cuối đã hiển thị. Mở trang không tự ghi dữ liệu bằng GET; JavaScript gửi POST có CSRF khi trang hiển thị, có nút dự phòng nếu JavaScript không hoạt động.
- Đóng/mở lại: `POST /admin/support/{id}/status` (`closed=true/false`). Hội thoại đã đóng vẫn giữ lịch sử và không nhận tin mới trước khi mở lại.

Người gửi, quyền quản trị và chủ hội thoại được xác định từ đăng nhập Spring Security và tài khoản hiện có trong cơ sở dữ liệu. Không nhận mã người gửi hoặc chủ hội thoại từ biểu mẫu. Mọi truy cập dữ liệu hội thoại đều kiểm tra quyền; hội thoại không tồn tại và hội thoại của tài khoản khác đều trả 404. Mọi thao tác ghi cần CSRF. Các thao tác gửi, đóng/mở và đánh dấu đã đọc dùng khóa hàng trong giao dịch để không ghi đè trạng thái đồng thời.

Nội dung thuần văn bản: tiêu đề 1–120 ký tự, nội dung 1–4.000 ký tự; kiểm tra phía máy chủ, hiển thị bằng Thymeleaf `th:text` để thoát HTML. Không có tệp đính kèm, gửi email, WebSocket hoặc kết nối với chatbot AI.

Tin từ khách hàng chuyển sang **Chờ phản hồi**; tin từ quản trị viên chuyển sang **Đã phản hồi**; đóng chuyển sang **Đã đóng**. Số chưa đọc của khách hàng tính tin quản trị viên; số chưa đọc quản trị tính tin khách hàng và dùng chung cho nhóm quản trị viên. Gửi phản hồi không tự đánh dấu các tin khác đã đọc. Thời gian dùng múi giờ Việt Nam.

Hai bảng mới: `support_conversations`, `support_messages`. Cấu hình `ddl-auto=update` hiện có tạo bảng khi khởi động. Với môi trường quản lý lược đồ thủ công, dùng [mã SQL](../src/main/resources/db/manual/20261112_support.sql) một lần thay cho tự động cập nhật. Không thay đổi bảng hay luồng thanh toán hiện có.

Kiểm thử: `SupportServiceTest` kiểm tra lưu trữ JPA, gửi/nhận, phân trang, trạng thái, chưa đọc, xác thực và quyền sở hữu. `SupportControllerTest` kiểm tra biểu mẫu, phản hồi quản trị, CSRF, phân quyền HTTP, thoát HTML và thông báo tiếng Việt. Kiểm thử dữ liệu dùng H2 riêng, không ghi vào MySQL ứng dụng.
