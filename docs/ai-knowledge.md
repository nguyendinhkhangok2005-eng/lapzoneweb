# Kiến thức AI

Quản trị tại `/admin/ai/knowledge`, mở từ **Kiến thức AI** trong thanh quản trị.
Các nhóm: câu hỏi thường gặp, bảo hành, đổi trả/hoàn tiền, thanh toán/giao hàng,
thông tin cửa hàng và hướng dẫn chọn mua laptop.

## Vòng đời

- Tạo mới và lưu chỉnh sửa đều tạo phiên bản nháp bất biến.
- Sửa mục đang xuất bản giữ nguyên phiên bản chatbot đang sử dụng. Cần lưu nháp,
  kiểm tra nội dung, chọn xác nhận rồi xuất bản để thay thế bản đang dùng.
- Ngừng xuất bản loại mục khỏi truy vấn tiếp theo. Lưu trữ ngừng sử dụng và khóa
  chỉnh sửa/xuất bản; lịch sử vẫn có thể xem để đối chiếu.
- Mỗi lần tạo, sửa, xuất bản, ngừng xuất bản và lưu trữ có phiên bản, tác giả và
  thời gian riêng. Khóa hàng và phiên bản kỳ vọng chặn biểu mẫu cũ ghi đè dữ liệu mới.
- Chỉ tài khoản có `ROLE_ADMIN` và vai trò quản trị còn hiệu lực trong DB được
  quản lý nội dung. Mọi biểu mẫu ghi dữ liệu cần CSRF.

## Trả lời và giới hạn dữ liệu

Truy vấn JPA tìm trong văn bản tiếng Việt chuẩn hóa, tối đa 6 từ khóa, 50 ứng viên
đã xuất bản và 2 mục liên quan. Không có cơ sở dữ liệu vector, bộ nhớ đệm câu trả
lời hay thay đổi mô hình. Các lần truy vấn mới đọc phiên bản đang xuất bản từ DB.

Câu hỏi chính sách/FAQ phù hợp nhận văn bản tham khảo trực tiếp. Câu hỏi kết hợp
sản phẩm giữ luồng tư vấn hiện tại và bổ sung tham khảo sau khi kiểm tra lại trạng
thái xuất bản. Kiến thức không được đưa vào chỉ lệnh hệ thống hay thực thi như mã.
Nội dung lịch sử vẫn là bản chụp tại thời điểm trả lời, có số phiên bản; không tự
đổi lịch sử cũ thành chính sách mới.

Nội dung tối đa 4.000 ký tự, văn bản thuần, được chuẩn hóa và thoát khi hiển thị.
HTML, mã nhúng, chỉ lệnh ghi đè AI và các mẫu dữ liệu sản phẩm riêng lẻ bị từ chối.
Hướng dẫn mua có thể nêu cấu hình khuyến nghị chung; giá/tồn kho/ảnh/cấu hình của
từng sản phẩm tiếp tục lấy từ danh mục sản phẩm. Quản trị viên phải kiểm tra nguồn
và hiệu lực thông tin trước mỗi lần xuất bản; hệ thống không tự xác minh chính sách
ngoài cửa hàng.

## Triển khai và kiểm thử

Hai bảng `ai_knowledge_entries`, `ai_knowledge_revisions` được tạo bằng
`ddl-auto=update`. Với schema quản lý thủ công, dùng
[migration SQL](../src/main/resources/db/manual/20261112_ai_knowledge.sql).

`AiKnowledgeServiceTest` kiểm tra cập nhật FAQ trong cùng hội thoại, bản nháp,
ngừng xuất bản/lưu trữ, lịch sử phiên bản, quyền, nội dung không hợp lệ và giữ dữ
liệu sản phẩm từ DB. `AdminAiKnowledgeControllerTest` kiểm tra phân quyền, CSRF,
biểu mẫu tiếng Việt và thoát nội dung khi hiển thị. Kiểm thử DB dùng H2 riêng,
transport Gemini được mô phỏng; không gọi dịch vụ AI thật hay huấn luyện mô hình.
