# Đánh giá câu trả lời AI

- Nút hữu ích/chưa hữu ích gắn với `messageId` trả về sau khi lưu câu trả lời.
  Lịch sử trả thêm `id` của từng tin nhắn. Không suy luận mã từ nội dung AI.
- Đánh giá tiêu cực bắt buộc chọn một trong năm lý do tiếng Việt. Không nhận nội
  dung tự do, bản chép hội thoại, tên hay thông tin liên hệ trong báo cáo quản trị.
- Chỉ chủ hội thoại đăng nhập hoặc phiên khách đang sở hữu hội thoại được gửi/xem
  đánh giá. Quyền quản trị không cho phép đánh giá thay khách hàng.
- Mỗi tin nhắn AI nhận một đánh giá không chỉnh sửa. Gửi lại cùng dữ liệu trả kết
  quả cũ; dữ liệu khác trả 409. Khóa hội thoại và ràng buộc duy nhất trong DB chống
  bản ghi trùng đồng thời. Tối đa 20 đánh giá mới/hội thoại/phút.
- POST đánh giá và đánh dấu xem xét cần CSRF. Giao diện lấy token cùng phiên từ
  `GET /api/ai/feedback/csrf`; các API tư vấn AI hiện có giữ nguyên hành vi.

## API và quản trị

| Đường dẫn | Chức năng |
| --- | --- |
| `GET /api/ai/conversations/{id}/feedback` | Đánh giá của hội thoại đang sở hữu |
| `POST /api/ai/conversations/{id}/messages/{messageId}/feedback` | Gửi `rating`, `reason` |
| `GET /admin/ai/feedback` | Lọc mức đánh giá, lý do, ngày; 20 bản ghi/trang |
| `POST /admin/ai/feedback/{id}/review` | Quản trị viên đánh dấu đã xem xét |

Mức đánh giá: `POSITIVE`, `NEGATIVE`. Lý do: `INCORRECT`, `IRRELEVANT`,
`BAD_RECOMMENDATION`, `UNCLEAR`, `OTHER`. Đánh giá tích cực có lý do `null`.
Thống kê tổng quan dùng thời gian gửi đánh giá, theo ngày Việt Nam.

Trang quản trị chỉ hiển thị mã hội thoại rút gọn, mã tin nhắn, mức đánh giá, lý do
và thời gian/trạng thái xem xét. Không có đường dẫn xem toàn bộ nội dung chat.
Không tự động huấn luyện, sửa prompt hoặc thay đổi mô hình từ đánh giá này.

## Cơ sở dữ liệu và kiểm thử

Bảng `ai_feedback` được Hibernate tạo với `ddl-auto=update`. Với lược đồ quản lý
thủ công, áp dụng [SQL](../src/main/resources/db/manual/20261112_ai_feedback.sql)
trước khi triển khai. Xóa/làm mới hội thoại xóa đánh giá liên quan bằng FK cascade.

Kiểm thử mới: `AiFeedbackServiceTest`, `AiFeedbackControllerTest` và
`node --test src/test/js/chatbot-feedback.test.cjs`. Kiểm thử DB dùng H2 riêng.
