# Yêu cầu trả hàng và hoàn tiền

Khách đăng nhập mở **Đơn hàng → Chi tiết → Yêu cầu hoàn tiền**. Chỉ dòng hàng thuộc đơn của tài khoản, có số lượng mua hợp lệ và đơn ở `HOÀN THÀNH` được gửi. Không áp dụng khoảng thời hạn tự đặt vì dự án chưa có chính sách thời hạn được cấu hình.

Khách chọn loại hoàn tiền hoặc trả hàng, số lượng 1 đến số lượng thực mua, lý do thuần văn bản 1–2.000 ký tự. Mỗi dòng mua hàng chỉ có một hồ sơ trong suốt lịch sử, kể cả khi đã từ chối hoặc hoàn tất. Không gửi tiếp phần số lượng còn lại bằng hồ sơ thứ hai. Hồ sơ toàn đơn cũ chặn yêu cầu mới cho tất cả dòng thuộc đơn đó. Giao diện nêu quy tắc này trước khi gửi; có thể liên hệ hỗ trợ nếu cần xem xét lại.

## Trạng thái và quyền

| Giá trị lưu | Nhãn | Chuyển tiếp bởi Admin |
| --- | --- | --- |
| `REQUESTED` | Chờ xử lý | Chấp nhận hoặc từ chối |
| `APPROVED` | Đã chấp nhận | Hoàn tất hồ sơ |
| `REJECTED` | Từ chối | Không chuyển tiếp |
| `COMPLETED` | Hoàn tất | Không chuyển tiếp |

`CANCELLED` chỉ giữ để đọc dữ liệu cũ, không cung cấp thao tác ghi mới. Gửi lại đúng trạng thái đã lưu là không đổi dữ liệu, không thêm lịch sử hay ghi đè ghi chú. Không bỏ qua phê duyệt, đổi từ chối thành chấp nhận, hoặc mở lại hồ sơ đã kết thúc.

Admin dùng `/admin/returns`, có bộ lọc và phân trang 20 hồ sơ. Từ chối bắt buộc có lý do. Hoàn tất bắt buộc có ghi chú/kết quả hoặc mã chứng từ và xác nhận đã đối soát, xử lý ngoài hệ thống. Lưu người duyệt/người hoàn tất, thời điểm và lịch sử bất biến trong `order_return_events`.

**Đây là quản lý yêu cầu, không phải thực thi giao dịch hoàn tiền.** Không có lệnh gọi cổng thanh toán, chuyển tiền, trả lại voucher, đổi tổng/đơn giá, đổi trạng thái đơn gốc hoặc tự nhập kho. Trạng thái hoàn tất ghi nhận việc Admin đã xác nhận xử lý hồ sơ ngoài hệ thống; không tạo chứng từ thanh toán giả. Việc nhập hàng trả lại cần quy trình tiếp nhận hàng thực tế riêng. Lịch sử đơn và giao dịch gốc được giữ nguyên.

## Điểm truy cập

- `GET/POST /store/orders/{orderId}/items/{itemId}/return-request`: biểu mẫu khách hàng và gửi yêu cầu.
- `POST /api/orders/{orderId}/items/{itemId}/return-requests`: JSON `{"quantity":1,"type":"REFUND","reason":"Sản phẩm gặp lỗi"}`; `type` chỉ nhận `REFUND` hoặc `RETURN`.
- `GET /api/orders/return-requests/{id}/history`: lịch sử cho chủ đơn hoặc Admin.
- `GET /admin/returns?status=REQUESTED&page=0`: danh sách quản trị.
- `POST /admin/returns/{id}/decision`: `status`, `note`, `manualProcessingConfirmed` (bắt buộc true khi hoàn tất).
- Các API và trang chi tiết đơn hiện có trả thêm số lượng yêu cầu, phản hồi duyệt và ghi chú hoàn tất. Nhóm **Trả hàng** tự lấy các đơn có hồ sơ thật. Nút yêu cầu ẩn khi sản phẩm đã có hồ sơ. API khả năng công khai báo `returnSubmissionSupported=true`, còn đánh giá vẫn chưa được mở.

Mọi thao tác ghi yêu cầu CSRF, kể cả JSON API. API dùng token từ phiên Spring Security, tên header lấy từ `CsrfToken.getHeaderName()` theo cấu hình hiện tại. Không nhận chủ đơn, người thao tác, trạng thái duyệt hoặc tiền hoàn do khách cung cấp. Đăng nhập chỉ dựa vào principal và tài khoản DB, không tin dữ liệu người dùng trong session. Dịch vụ kiểm tra quyền sở hữu cả đơn và dòng hàng; đối tượng không có quyền truy cập được trả 404. Giao diện dùng `th:text` để thoát HTML.

## Giao dịch, chống trùng và lược đồ

Gửi yêu cầu khóa đơn theo chủ sở hữu, đọc các hồ sơ bằng khóa hiện thời, rồi kiểm tra điều kiện và số lượng. Khóa được lấy theo thứ tự đơn → hồ sơ cho cả gửi và duyệt. Ràng buộc unique trên `order_detail_id` là lớp bảo vệ cuối cùng khi gửi trùng đồng thời. Thay đổi trạng thái và ghi sự kiện nằm trong cùng giao dịch; lỗi ghi lịch sử làm hoàn tác toàn bộ thao tác.

Giữ nguyên bảng `order_return_cases`, bổ sung trường quản trị/số lượng, thêm `order_return_events`. Dữ liệu cũ không biết số lượng vẫn giữ NULL; không dựng lại lịch sử. Cấu hình `ddl-auto=update` hiện tại áp dụng mapping khi khởi động; với quản lý lược đồ thủ công, dùng [SQL nâng cấp](../src/main/resources/db/manual/20261112_return_requests.sql) một lần. SQL có truy vấn kiểm tra dữ liệu trùng cũ trước khi thêm unique; không tự xóa hay gộp hồ sơ.

Kiểm thử `ReturnRequestServiceTest` bao gồm quyền sở hữu, trạng thái, số lượng, gửi đồng thời, ràng buộc unique, rollback khi lỗi lịch sử và bảo toàn giá/voucher/tồn kho. `ReturnRequestControllerTest` kiểm tra biểu mẫu/JSON, CSRF, phân quyền Admin, thoát HTML và thông báo tiếng Việt. Kiểm thử dùng H2 riêng, không ghi MySQL ứng dụng.
