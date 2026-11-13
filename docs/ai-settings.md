# Cấu hình AI

Trang quản trị: `/admin/ai/settings`. Chỉ `ROLE_ADMIN` và tài khoản còn vai trò
`ADMIN` trong DB được lưu, khôi phục hoặc xem lịch sử thay đổi. POST cần CSRF.

## Giá trị và tác động

| Tùy chọn | Giá trị được phép | Mặc định |
| --- | --- | --- |
| Phong cách | Thân thiện, chuyên nghiệp, ngắn gọn | Thân thiện |
| Độ dài lời dẫn | Ngắn, trung bình, chi tiết | Trung bình |
| Sản phẩm tối đa | Số nguyên 1–5 | 3 |
| Đánh dấu ưu đãi đủ điều kiện | Có/Không | Không |
| Thêm sản phẩm liên quan | Có/Không | Không |

Giá trị được kiểm tra ở backend và đọc lại từ DB cho từng lượt tư vấn mới; không
có bộ nhớ đệm làm chậm hiệu lực thay đổi. Phong cách/độ dài chỉ thay lời dẫn và
định dạng được phép gửi mô hình. Văn bản kiến thức đã xuất bản được giữ trọn vẹn,
không bị rút ngắn làm mất điều kiện chính sách. Prompt an toàn, kiểm tra sản phẩm,
quyền và giới hạn yêu cầu không thể chỉnh từ trang này.

Giới hạn thẻ áp dụng sau khi xác minh lại ID và dữ liệu sản phẩm. Khi bật gợi ý
liên quan, backend chỉ thêm sản phẩm cùng danh mục còn phù hợp yêu cầu/ngân sách
và xác minh lại với DB; không thêm nếu phản hồi AI không có sản phẩm hợp lệ. Nhãn
ưu đãi lấy tỷ lệ từ `PromotionService` và chỉ hiển thị cho sản phẩm có giá hợp lệ,
còn hàng. Giá/thông số/ảnh/tồn kho trên thẻ luôn lấy từ DB.

**Xem trước** chỉ dựng văn bản minh họa từ các tùy chọn đang nhập: không lưu, không
gọi mô hình và không tạo sản phẩm/số liệu giả. Nút mở **Kiểm tra gợi ý AI** dẫn tới
bài thử thật, chỉ chạy khi quản trị viên bấm gửi ở trang đó.

## Lưu và kiểm toán

`ai_response_settings` lưu một cấu hình cùng phiên bản. `ai_response_settings_changes`
lưu thời gian, mã quản trị viên, thao tác cập nhật/khôi phục và giá trị trước/sau.
Phiên bản kỳ vọng từ biểu mẫu ngăn ghi đè một thay đổi mới hơn. Lịch sử có phân
trang. Không bảng nào chứa khóa API, mật khẩu hoặc thông tin xác thực.

Với `ddl-auto=update`, Hibernate tạo bảng khi triển khai. Nếu quản lý schema thủ
công, dùng [migration SQL](../src/main/resources/db/manual/20261112_ai_response_settings.sql).

Kiểm thử: `AiResponseSettingsServiceTest`, `AiResponseSettingsConsultationTest`,
`AiResponseSettingsIntegrationTest`, `AdminAiSettingsControllerTest`, và các kiểm
thử hồi quy chatbot hiện có. DB kiểm thử là H2 riêng; transport AI được mô phỏng.
