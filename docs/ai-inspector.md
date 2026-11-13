# Kiểm tra gợi ý AI

Mở `/admin/ai/inspector` từ **Kiểm tra gợi ý AI** trong quản trị. Nạp một trong
năm câu hỏi mẫu (chơi game, văn phòng, lập trình, đồ họa, ngân sách), hoặc chỉnh
câu hỏi, rồi bấm **Chạy một bài kiểm tra**.

## Phạm vi đối chiếu

- Dùng `AiConsultantService` với lịch sử rỗng; không tạo `AiConversation` hoặc
  `AiMessage`, không ảnh hưởng thống kê/hội thoại khách hàng.
- Đối chiếu các sản phẩm có cấu trúc mà dịch vụ tư vấn trả về. Đây không phải
  trình kiểm tra văn bản thô từ nhà cung cấp; mã đã bị backend loại không xuất hiện lại.
- Truy vấn scalar hiện tại từ DB để kiểm tra mã, tên, quan hệ ảnh, giá theo quy
  tắc khuyến mại cửa hàng, thông số hiển thị, yêu cầu cấu hình đã nhận diện, ngân
  sách, tồn kho, khả năng mua và liên kết chi tiết.
- `PASS` chỉ áp dụng cho trường có bằng chứng khớp. `FAIL` chỉ sai lệch/vi phạm.
  `NOT VERIFIED` áp dụng khi thiếu dữ liệu, không nhận diện được điều kiện, không
  có sản phẩm, hoặc chưa có bằng chứng về mức phù hợp/hiệu năng chủ quan.
- Kiểm tra ảnh chỉ xác minh quan hệ với sản phẩm trong DB, không tải ảnh hoặc
  kiểm tra nội dung điểm ảnh. Ảnh chưa xác minh không được nạp trong trang kết quả.
- Các giới hạn của bộ trích xuất yêu cầu hiện có vẫn được ghi rõ; kết quả không
  chứng minh đây là sản phẩm tốt nhất hoặc mọi diễn đạt tự do đã được hiểu đúng.

## Lưu trữ và giới hạn sử dụng

Mỗi lần chạy lưu câu hỏi, người chạy, loại câu hỏi, thời gian bắt đầu/kết thúc,
trạng thái và bản chụp JSON của phản hồi/bằng chứng đối chiếu. Mở lại kết quả chỉ
đọc bản chụp, không gọi AI hay tự cập nhật theo giá mới.

Chỉ POST có CSRF và `ROLE_ADMIN` mới chạy. Tài khoản còn phải có vai trò quản trị
trong DB. Mỗi yêu cầu có UUID duy nhất; gửi trùng trả về kết quả cũ. Mỗi quản trị
viên tối đa 10 bài/giờ, cách nhau ít nhất 30 giây, chặn chạy chồng khi bài trước
còn đang xử lý trong hai phút đầu. Giữ nguyên retry/fallback có giới hạn của dịch
vụ tư vấn hiện tại. Không có đường chạy lô, tác vụ lịch hay tự chạy khi mở trang.

Không có thao tác sửa sản phẩm, điều chỉnh prompt hoặc huấn luyện mô hình.

Bảng `ai_inspection_runs` được tạo với `ddl-auto=update`. Nếu quản lý schema thủ
công, áp dụng [SQL](../src/main/resources/db/manual/20261112_ai_inspector.sql).

## Kiểm thử

`AiRecommendationValidatorTest` kiểm tra dữ kiện sai/thiếu, ngân sách dùng giá DB,
ảnh thuộc sản phẩm và mức chất lượng chưa xác minh. `AiInspectionServiceTest`
kiểm tra lưu trữ, số lần tư vấn, giới hạn, quyền, cô lập hội thoại và dữ liệu sản
phẩm, bản chụp khi DB thay đổi. `AdminAiInspectionControllerTest` kiểm tra CSRF,
phân quyền, chỉ chạy sau POST, ba trạng thái và thoát nội dung khi hiển thị.
Kiểm thử dùng H2 riêng và mô phỏng dịch vụ tư vấn, không gọi API AI thật.
