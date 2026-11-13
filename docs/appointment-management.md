# Quản lý lịch hẹn

## Dữ liệu và quyền sở hữu

- Giữ nguyên bảng `appointments`, các cột hiện có, mã lịch hẹn và quy trình `GET/POST /datlich`.
- [Script ba bảng bổ sung](../src/main/resources/db/manual/20261113_appointment_management.sql) chỉ để xem xét, không được chạy lên cơ sở dữ liệu thật trong quá trình phát triển. Cấu hình `ddl-auto` hiện tại không thay đổi.
- `appointment_management` liên kết với bản ghi gốc bằng khóa chính `appointment_id`; lưu tài khoản sở hữu, thời lượng, thời điểm cập nhật và lý do gần nhất.
- `appointment_status_history` lưu trạng thái trước/sau, thời điểm, lý do và tài khoản thực hiện. Tạo lịch và ghi lịch sử nằm trong cùng giao dịch.
- Chủ sở hữu lấy từ `CustomUserDetails` đã xác thực và kiểm tra lại trong bảng `users`. Thông tin liên hệ trên biểu mẫu có thể khác thông tin tài khoản; không dùng chúng để xác lập quyền sở hữu.
- Lịch của khách chưa đăng nhập và lịch cũ không có liên kết sở hữu chỉ được Admin truy cập. Không tự gán lịch theo email/số điện thoại, không có API nhận quyền sở hữu bằng thông tin liên hệ. Chưa cung cấp quy trình xác minh hoặc nhận lại lịch của khách vãng lai.
- Trạng thái cũ ngoài bộ trạng thái mới vẫn được đọc; không tự đổi dữ liệu hoặc cho phép chuyển tiếp trạng thái không rõ nghĩa.

## Quy tắc trạng thái

| Thao tác | Trạng thái trước | Trạng thái sau | Điều kiện |
| --- | --- | --- | --- |
| Tạo lịch | Chưa có | `PENDING` – Chờ xác nhận | Ngày/giờ tương lai theo giờ Việt Nam; dữ liệu hợp lệ |
| Admin xác nhận | `PENDING` | `CONFIRMED` – Đã xác nhận | Chưa đến giờ; cửa hàng và sức chứa hợp lệ nếu đã cấu hình |
| Admin từ chối | `PENDING` | `REJECTED` – Đã từ chối | Bắt buộc lý do |
| Admin hoàn thành | `CONFIRMED` | `COMPLETED` – Hoàn thành | Đã đến thời gian hẹn |
| Khách hủy | `PENDING` | `CANCELLED` – Đã hủy | Chính chủ, chưa đến giờ hẹn, bắt buộc lý do |

Mã cũ không có quy tắc hủy. Chính sách mới giới hạn hủy ở lịch chờ xác nhận trong tương lai. Các trạng thái kết thúc không được mở lại; không hỗ trợ đổi lịch/cửa hàng đã chọn bằng thao tác cập nhật trạng thái.

## API và trang hiện có

| Phương thức và đường dẫn | Quyền | Kết quả |
| --- | --- | --- |
| `GET /api/appointments` | Đã đăng nhập | Lịch do chính tài khoản sở hữu |
| `GET /api/appointments/{id}` | Chính chủ | Chi tiết và lịch sử trạng thái |
| `POST /api/appointments/{id}/cancel` | Chính chủ + CSRF | Hủy với JSON `{"reason":"Lý do hủy"}` |
| `GET /api/appointments/csrf` | Đã đăng nhập | Tên tiêu đề, tên tham số và mã CSRF |
| `GET /admin/appointments` | Admin | Danh sách thật, bao gồm lịch cũ/khách vãng lai |
| `GET /admin/appointments/{id}` | Admin | Chi tiết và lịch sử |
| `POST /admin/appointments/{id}/status` | Admin + CSRF | JSON `{"status":"CONFIRMED"}` hoặc `{"status":"REJECTED","reason":"Lý do"}` |
| `GET /admin/support?tab=appointments` | Admin | Bảng lịch thật, bộ lọc, phân trang và thao tác |
| `POST /admin/support/appointments/{id}/status` | Admin + CSRF | Biểu mẫu cập nhật, quay lại thẻ lịch hẹn |

Danh sách nhận `status` (tùy chọn), `page` từ 0, `size` từ 1 đến 100; trang Admin hỗ trợ cố định 20 dòng. Khách không được truy cập lịch người khác hoặc lịch chưa có chủ sở hữu: trả cùng mã 404 như lịch không tồn tại. API khách yêu cầu đăng nhập trả 401; thiếu quyền/CSRF trả 403; dữ liệu sai trả 400; chuyển trạng thái hoặc đặt lịch xung đột trả 409.

Giao diện Admin dùng thêm tham số `appointmentId` trên chính `/admin/support?tab=appointments` để mở chi tiết và lịch sử, không tạo đường dẫn API mới. Bộ đếm trên hai thẻ là số lịch thật có trạng thái `PENDING`, không phải số lịch chưa đọc. Thao tác giữ bộ lọc và trang đang xem; nếu trang cuối không còn dòng phù hợp thì hiển thị trang còn dữ liệu gần nhất. Biểu mẫu xác nhận thao tác, yêu cầu lý do từ chối và khóa nút khi đang gửi; dịch vụ vẫn kiểm tra lại quyền và trạng thái trong giao dịch để chặn yêu cầu lặp hoặc dữ liệu đã cũ.

Lịch `PENDING` chưa chọn cửa hàng vẫn được tạo bằng biểu mẫu hiện có. Khi có danh sách cửa hàng cấu hình, Admin phải truyền thêm `showroom` khi xác nhận lịch chưa có cửa hàng. Giá trị phải khớp tên hoặc địa chỉ duy nhất đã cấu hình.

## Cấu hình giờ và sức chứa

Tái sử dụng `lapzone.stores.branches[n]` trong danh bạ cửa hàng hiện có. Không tự điền giờ, sức chứa hoặc thời lượng giả vào cấu hình chạy thật.

| Thuộc tính của cửa hàng | Ý nghĩa |
| --- | --- |
| `name`, `address` | Tên/địa chỉ để kiểm tra và chuẩn hóa cửa hàng |
| `opening-hours` | Hỗ trợ khoảng giờ hằng ngày như `08:00–20:00` hoặc `08:00-20:00` |
| `appointment-opens-at`, `appointment-closes-at` | Giờ `HH:mm` có cấu trúc, ưu tiên dùng khi giờ mô tả có định dạng phức tạp |
| `appointment-days` | Danh sách ngày nhận lịch, ví dụ `MONDAY,TUESDAY`; không khai báo nghĩa là không giới hạn ngày trong tuần |
| `appointments-enabled` | `false` để ngừng nhận lịch tại cửa hàng; mặc định `true` |
| `appointment-duration-minutes` | Thời lượng thực tế, từ 1 đến 1440 phút; lịch phải kết thúc trong cùng ngày |
| `appointment-capacity` | Số lịch đồng thời thực tế; số nguyên dương và bắt buộc có thời lượng khi cấu hình |

Không có cửa hàng cấu hình: giữ thông tin cửa hàng mong muốn như trước, không tự áp giới hạn giờ/sức chứa. Có danh bạ: từ chối tên không xác định hoặc trùng định danh. Giờ mô tả không đọc được sẽ chặn nhận/xác nhận tại cửa hàng đó cho đến khi có giờ cấu trúc hợp lệ. Nếu chỉ có tên cửa hàng và không có giới hạn giờ/sức chứa thì không tự tạo các giới hạn đó.

Lịch `PENDING` và `CONFIRMED` đã chọn cửa hàng chiếm sức chứa. Kiểm tra các khoảng thời gian giao nhau, cho phép hai khoảng tiếp giáp. Lịch mới lưu thời lượng tại lúc đặt; lịch cũ chưa lưu thời lượng dùng thời lượng cấu hình hiện tại khi kiểm tra sức chứa. Việc hủy/từ chối giải phóng sức chứa sau khi giao dịch hoàn tất.

Khóa `appointment_booking_locks` được tạo/khóa bằng một lệnh MySQL `INSERT ... ON DUPLICATE KEY UPDATE` trong giao dịch, theo cửa hàng/ngày. Giao dịch ghi dùng `READ_COMMITTED` để hạn chế khóa khoảng không cần thiết; truy vấn khóa các lịch còn hiệu lực dùng đọc khóa để thấy dữ liệu đã được lưu sau khi chờ. Cập nhật trạng thái khóa bản ghi lịch; kiểm tra lại thời gian và trạng thái sau khi chờ khóa. Lỗi chờ khóa được trả về như xung đột, không lưu lịch một phần.

## Kiểm tra thủ công

1. Đăng nhập khách, đặt lịch ở `/datlich`, xem lịch của tài khoản tại `/api/appointments`.
2. Đăng nhập tài khoản khác: danh sách không có lịch trên; đọc/hủy mã lịch đó bị từ chối.
3. Admin mở `/admin/support?tab=appointments`, lọc trạng thái, xác nhận/từ chối lịch; xem trạng thái và lịch sử tại `/admin/appointments/{id}`.
4. Khách hủy lịch chờ xác nhận trong tương lai với lý do; lịch đã xác nhận không thể hủy.
5. Cấu hình sức chứa/giờ thực tế trên môi trường thử nghiệm, gửi hai yêu cầu đồng thời tới khung giờ chỉ còn một chỗ: chỉ một yêu cầu được lưu.

Các bài kiểm thử dùng Mockito và H2 độc lập ở chế độ tương thích MySQL; không chạy SQL trên MySQL thật. Chưa xác minh hành vi khóa trực tiếp trên máy chủ MySQL triển khai.
