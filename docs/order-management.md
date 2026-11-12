# Quản lý đơn hàng

Đã kiểm tra [Order](../src/main/java/com/lapzone/lapzoneweb/model/entity/Order.java), [OrderDetail](../src/main/java/com/lapzone/lapzoneweb/model/entity/OrderDetail.java), [dịch vụ đơn hàng](../src/main/java/com/lapzone/lapzoneweb/model/service/OrderService.java), [quản trị đơn hàng](../src/main/java/com/lapzone/lapzoneweb/controller/AdminOrderController.java) và [thanh toán](../src/main/java/com/lapzone/lapzoneweb/controller/CheckoutController.java).

Đơn hiện có một trường trạng thái chung cho thanh toán và vận chuyển. Checkout tạo đơn COD ở `CHỜ XÁC NHẬN`, đơn trực tuyến ở `CHỜ THANH TOÁN`; trừ tồn kho lúc tạo và lưu giá sau khuyến mãi trong từng dòng hàng. Các luồng tạo đơn, tính giá, áp dụng voucher và checkout giữ nguyên.

## Nhóm dành cho khách hàng

| Khóa API | Nhãn | Dữ liệu thực tế |
| --- | --- | --- |
| `ALL` | Tất cả | Tất cả đơn gắn với tài khoản đăng nhập, kể cả chờ thanh toán, đã hủy và trạng thái cũ chưa nhận diện |
| `PENDING` | Chờ xác nhận | `CHỜ XÁC NHẬN` (COD), `CHỜ THANH TOÁN` (đơn trực tuyến như VNPay chưa thanh toán), `ĐÃ THANH TOÁN` (đã đối soát, còn chờ xác nhận xử lý đơn) |
| `SHIPPING` | Chờ vận chuyển | `ĐANG ĐÓNG GÓI`, `ĐANG GIAO HÀNG`; đọc tương thích giá trị cũ `ĐANG GIAO` |
| `REVIEW` | Cần đánh giá | `HOÀN THÀNH` và còn dòng hàng chưa có bản ghi đánh giá |
| `RETURNS` | Trả hàng | Có ít nhất một hồ sơ trả hàng/hoàn tiền gắn với đơn hoặc dòng hàng, kể cả hồ sơ đã kết thúc |
| `CANCELLED` | Đã hủy | `ĐÃ HỦY` |
| `COMPLETED` | Hoàn thành | `HOÀN THÀNH` và không còn dòng có số lượng dương chưa được đánh giá |

Các nhóm có thể giao nhau, ví dụ đơn đã giao có hồ sơ trả hàng và còn sản phẩm chưa đánh giá. Không tạo trạng thái đơn mới. `ĐANG ĐÓNG GÓI` có nghĩa Admin đã xác nhận; `HOÀN THÀNH` có nghĩa đã giao. Không suy luận đã giao chỉ từ `ĐÃ THANH TOÁN`.

Danh sách từng nhóm, tổng phân trang và badge số đếm dùng cùng điều kiện `CUSTOMER_ORDER_FILTER` trong repository, luôn có `o.user.id = :userId`. Danh sách chọn `distinct o`, số đếm chọn `count(distinct o.id)`; đánh giá và trả hàng dùng `exists` để nhiều dòng sản phẩm/hồ sơ không nhân số đơn. `ALL` chỉ có điều kiện chủ sở hữu, không hạn chế trạng thái hay việc đã đánh giá. Đơn hoàn thành đã đánh giá vẫn nằm trong `ALL` và xuất hiện ở `COMPLETED`. Trạng thái lạ, trống hoặc thanh toán thất bại vẫn nằm trong `ALL`.

Các tab là bộ lọc giao nhau, không phải nhóm chính độc quyền; UI thông báo rõ không cộng số trên các tab để tính tổng. Vì vậy không áp dụng ưu tiên loại bỏ đơn trả hàng khỏi nhóm cần đánh giá/chờ vận chuyển. Mỗi tab đếm một đơn tối đa một lần; đơn có hồ sơ trả hàng vẫn có thể xuất hiện ở nhóm khác đúng điều kiện. Nhóm `REVIEW` chỉ hiển thị sản phẩm còn cần đánh giá; số trên tab là số đơn, tổng tiền vẫn là toàn bộ đơn. List và badge được đọc trong cùng giao dịch `REPEATABLE_READ`; API `/api/orders/categories` trả `counts` và `categoriesMayOverlap=true`, còn mỗi trang API trả `categoryCounts`.

Đối chiếu MySQL chỉ đọc ngày 12/11/2026 cho tài khoản ID 1:

| Nhóm | Số đơn | ID theo thứ tự mới nhất |
| --- | ---: | --- |
| Tất cả | 6 | #10, #9, #7, #6, #5, #2 |
| Chờ xác nhận | 2 | #10 (COD chờ xác nhận), #7 (VNPay đã thanh toán) |
| Chờ vận chuyển | 1 | #6 (đang giao hàng) |
| Cần đánh giá | 1 | #5 |
| Trả hàng | 1 | #5 |
| Đã hủy | 2 | #9, #2 |
| Hoàn thành, không còn cần đánh giá | 0 | Không có |

SQL sở hữu, dịch vụ, bộ lọc phân trang, badge, API và model Thymeleaf khớp nhau; **không thiếu đơn sở hữu nào**. #5 được tính ở hai bộ lọc khác nhau đúng điều kiện, không phải đếm lặp trong một nhóm. Tài khoản ID 2 có số đếm 0 ở mọi nhóm. #1 vẫn có `user_id=NULL`, trạng thái `ĐÃ HỦY`, không được tự gán theo tên/SĐT. Các kiểm tra dùng principal kiểm thử của tài khoản DB thật, không phải cookie trình duyệt.

Lần sửa bộ lọc/số đếm đã chạy **43 kiểm thử đạt, 0 lỗi, 0 bỏ qua**: 39 kiểm thử mock/H2 về trang, bộ lọc, quyền sở hữu, đánh giá/trả hàng và 4 kiểm thử MySQL ép `READ ONLY`, `ddl-auto=none`. Kiểm thử thực tế đối chiếu từng ID qua nhiều trang và đếm distinct từ SQL độc lập; không tạo dữ liệu trong MySQL. Tập dữ liệu thật hiện không có đơn của ID 1 đang đóng gói, VNPay chưa thanh toán hoặc đã hoàn thành đánh giá; các nhánh này được kiểm tra bằng H2, không được coi là đã kiểm tra trên bản ghi MySQL thật.

Nhóm khách hàng `PENDING` trước đây thiếu `CHỜ THANH TOÁN`, khiến đơn VNPay chưa thanh toán của đúng chủ tài khoản không xuất hiện trong tab này. Việc bổ sung chỉ thay đổi nhóm đọc; không ghi trạng thái, xác nhận giao dịch hay cho phép giao đơn chưa thanh toán. Badge hiển thị riêng “Chờ xác nhận” và “Chờ thanh toán”; danh sách cũng hiển thị phương thức thanh toán. Admin vẫn đọc được đơn của mọi khách, còn khách hàng chỉ đọc đơn có `orders.user_id` đúng tài khoản xác thực. Đơn khách vãng lai (`user_id` trống) hoặc thuộc tài khoản khác vẫn không hiện, kể cả trùng tên/số điện thoại. Không tự liên kết hay sửa quyền sở hữu đơn cũ.

### Badge thanh toán và giao nhận dành cho khách hàng

[Bộ định dạng](../src/main/java/com/lapzone/lapzoneweb/controller/OrderViewFormat.java) và [fragment dùng chung](../src/main/resources/templates/orders/status.html) tách hai loại badge trên danh sách và chi tiết, chỉ đọc dữ liệu hiện có:

| Trạng thái đang lưu | Badge thanh toán | Badge giao nhận |
| --- | --- | --- |
| CHỜ XÁC NHẬN (COD) | Không suy luận thanh toán | Chờ xác nhận đơn hàng |
| CHỜ THANH TOÁN (VNPay) | Chờ thanh toán | Chưa có trạng thái giao nhận được xác nhận |
| ĐÃ THANH TOÁN | Đã thanh toán | Chờ xác nhận đơn hàng |
| ĐANG ĐÓNG GÓI | Không suy luận thêm | Đang đóng gói |
| ĐANG GIAO HÀNG / ĐANG GIAO | Không suy luận thêm | Đang giao hàng |
| HOÀN THÀNH | Không suy luận thêm | Hoàn thành |
| ĐÃ HỦY | Không suy luận đã trả/hoàn tiền | Đã hủy |
| THANH TOÁN THẤT BẠI | Thanh toán thất bại | Không hiển thị tiến độ giao nhận |

Phương thức VNPay, việc đóng gói/giao hàng hoặc mã đơn không phải bằng chứng để tạo badge “Đã thanh toán”. Chỉ giá trị `ĐÃ THANH TOÁN` được lưu bởi hệ thống hiện có tạo badge này. Schema MySQL được kiểm tra không có bảng payment/transaction riêng; thay đổi trình bày này không xác minh thêm giao dịch tại cổng VNPay và không dựng chứng từ thanh toán. Khi trạng thái chung đã chuyển sang giao nhận, UI không tự khẳng định thêm lịch sử thanh toán/hoàn tiền. Không thêm cột, không sửa API trạng thái gốc hay quy tắc chuyển trạng thái Admin.

Kiểm tra MySQL chỉ đọc ngày 12/11/2026: #7 thuộc ID 1, vẫn có `ĐÃ THANH TOÁN`, hiển thị hai badge và có trong `PENDING`; #1 tại lần kiểm tra này có `ĐÃ HỦY`, `user_id = NULL`, ánh xạ “Đã hủy” nhưng không được tiết lộ trên trang khách hàng. Kiểm thử không sửa hai bản ghi này. Các phiên xác thực là phiên kiểm thử từ tài khoản DB thật, không phải phiên trình duyệt người dùng.

Kiểm thử lần thay đổi badge: **45 đạt, 0 lỗi, 0 bỏ qua** — `OrderViewFormatTest` (5), `CustomerOrdersPageTest` (9), `OrderWorkflowTest` (9), `OrderManagementSecurityTest` (3), `CheckoutControllerTest` (9), `VoucherSecurityTest` (7), `CustomerOrderMySqlReadOnlyTest` (3). MySQL được ép connection/transaction `READ ONLY`, `ddl-auto=none`; không thêm dữ liệu mẫu. Chưa kiểm tra trực quan trên trình duyệt; CSS dùng biến theme hiện có và nhóm badge có khả năng xuống dòng trên màn hình hẹp.

## Chuyển trạng thái

| Hiện tại | Bước tiếp theo được phép |
| --- | --- |
| CHỜ THANH TOÁN | ĐÃ THANH TOÁN, THANH TOÁN THẤT BẠI, ĐÃ HỦY |
| CHỜ XÁC NHẬN / ĐÃ THANH TOÁN | ĐANG ĐÓNG GÓI, ĐÃ HỦY |
| ĐANG ĐÓNG GÓI | ĐANG GIAO HÀNG, ĐÃ HỦY |
| ĐANG GIAO HÀNG / ĐANG GIAO | HOÀN THÀNH |
| HOÀN THÀNH / ĐÃ HỦY / THANH TOÁN THẤT BẠI | Không chuyển tiếp |

Gửi lại đúng trạng thái hiện tại là thao tác không đổi dữ liệu, không hoàn kho hay ghi lịch sử lần hai. Giá trị lạ và việc bỏ qua/quay ngược bước bị từ chối. Đơn đang vận chuyển hoặc đã giao phải dùng quy trình trả hàng riêng thay vì hủy trực tiếp và hoàn kho chưa được tiếp nhận.

Admin được kiểm tra qua Spring Security và vai trò tài khoản hiện tại trong DB, kể cả khi gọi trực tiếp dịch vụ. Khóa hàng đơn hàng trong giao dịch bảo vệ cập nhật đồng thời. Chính sách cũng áp dụng khi sửa trạng thái qua màn hình hóa đơn để tránh đường vòng. Mỗi thay đổi thực tế ghi người thao tác, trạng thái trước/sau và thời gian vào `order_status_changes`; không dựng lịch sử cho đơn cũ.

Chuyển trạng thái không sửa tổng tiền, giá dòng hàng hoặc trừ kho lần nữa. Hủy trước vận chuyển hoàn kho đúng một lần theo cơ chế hiện có. Thanh toán thất bại cũng hoàn kho đúng một lần cho cả đơn không dùng voucher, khắc phục trường hợp giữ tồn kho ở nhánh cũ. Các mốc giữ/dùng/hoàn lượt voucher vẫn do dịch vụ voucher hiện có xử lý. Đơn đã thanh toán bị hủy không tự hoàn tiền hoặc cấp lại voucher đã dùng. Không thêm hay sửa tích hợp cổng thanh toán và báo cáo doanh thu hiện có.

## API đọc

Giao diện khách hàng `GET /store/orders` hiển thị bảy tab theo thứ tự bảng trên, lấy dữ liệu bằng cùng [dịch vụ phân quyền](../src/main/java/com/lapzone/lapzoneweb/model/service/CustomerOrderService.java) với API. `GET /store/orders/{id}` hiển thị chi tiết, lịch sử cập nhật và hồ sơ trả hàng có thật của chủ đơn. Các trang dùng Thymeleaf, CSS và giao diện sáng/tối/tùy chỉnh sẵn có. Mục **Đơn hàng** ở `/store` dẫn tới giao diện mới; tra cứu bằng mã đơn và số điện thoại vẫn giữ đường dẫn cũ. Ảnh hiển thị từ đường dẫn sản phẩm lưu trong DB, có trạng thái thiếu ảnh; đơn giá từng dòng và tổng tiền hiển thị từ bản ghi mua hàng đã lưu, không lấy giá bán mới trên website.

- `GET /api/orders/categories`: bảy nhóm, số đơn từng nhóm, cờ nhóm có thể giao nhau và khả năng gửi đánh giá/yêu cầu trả hàng.
- `GET /api/orders?category=ALL&page=0&size=20`: phân trang, giới hạn 1–50 đơn/trang; sắp theo ngày đặt rồi mã đơn mới nhất.
- `GET /api/orders/{id}`: đơn và lịch sử trạng thái thực tế.
- `GET /api/orders/{id}/items/{itemId}`: dòng hàng thuộc đơn của tài khoản hiện tại.
- `GET /admin/orders/{id}/data`: cùng dữ liệu chi tiết, dành cho Admin.
- `POST /admin/orders/update-status`: giữ endpoint quản trị hiện có, yêu cầu quyền Admin và CSRF; lựa chọn trên giao diện lấy từ chính sách chuyển trạng thái.

API tài khoản luôn lấy người dùng từ principal đăng nhập và DB; không nhận mã chủ đơn từ request hoặc tin vào `session.currentUser`. Không gán đơn khách vãng lai cho tài khoản bằng số điện thoại. Đơn hoặc dòng hàng không thuộc tài khoản trả 404; chưa đăng nhập trả JSON 401. Không serialize entity User, mật khẩu hoặc graph JPA. Tra cứu bằng mã đơn/số điện thoại và trang hóa đơn hiện có giữ đường dẫn cùng chính sách tương thích; người đã đăng nhập không thể dùng chúng để mở đơn của tài khoản khác.

DTO trả trạng thái gốc, phương thức thanh toán, tổng đã lưu, voucher đã lưu, nhóm hiện tại và từng dòng với mã dòng/sản phẩm, tên/ảnh sản phẩm hiện tại, số lượng, đơn giá lúc đặt, thành tiền dòng, `needsReview`, đánh giá và mã hồ sơ trả hàng liên quan. Thành tiền dòng chỉ để hiển thị; tổng phải trả luôn là tổng đã lưu của đơn. Trạng thái giao nhận dùng chung toàn đơn, không giả lập giao từng sản phẩm.

## Đánh giá và trả hàng

Trước thay đổi chưa có mô hình đánh giá/trả hàng. Bổ sung các liên kết dữ liệu để nhóm đơn dựa trên bản ghi thực:

- `order_item_reviews`: một đánh giá cho mỗi dòng mua hàng, mức 1–5 và thời điểm tạo; chủ đánh giá suy ra qua chủ đơn.
- `order_return_cases`: loại trả hàng/hoàn tiền, trạng thái hồ sơ, lý do và thời điểm tạo; `order_detail_id` để trống cho hồ sơ cả đơn. Kiểm tra JPA không cho gắn sản phẩm của đơn khác.

Khách được gửi một đánh giá 1–5 sao và nội dung viết cho mỗi dòng hàng đã giao (`HOÀN THÀNH`). Dịch vụ xác nhận chủ đơn từ tài khoản đăng nhập, khóa đơn khi ghi và dựa vào ràng buộc duy nhất của `order_item_reviews` để chống trùng. Khi dòng hàng đã có đánh giá, đơn biến mất khỏi nhóm `REVIEW` nếu không còn dòng nào khác cần đánh giá. `POST /store/orders/{orderId}/items/{itemId}/review` là form có CSRF.

Nút `Mua lại` trên dòng đã giao chỉ gọi `POST /store/orders/{orderId}/items/{itemId}/repurchase` để thêm một sản phẩm vào giỏ. Dịch vụ kiểm tra chủ đơn, sản phẩm hiện còn bán, giá hiện tại hợp lệ và tồn kho; giỏ và checkout dùng giá/khuyến mãi đang áp dụng. Không tạo đơn mới hoặc thay đổi đơn cũ. Giá và tồn kho vẫn được kiểm tra lại ở checkout. Đã có [quản lý yêu cầu trả hàng/hoàn tiền](./return-requests.md) độc lập; API báo `returnSubmissionSupported=true`, `reviewSubmissionSupported=true`. Hoàn tất hồ sơ không thực thi giao dịch hoàn tiền thật. Chưa có hồ sơ thì nhóm trả hàng trả danh sách rỗng. Không thêm dữ liệu mẫu, đơn giả hoặc tự tạo hồ sơ từ trạng thái thanh toán/hủy đơn.

## Lược đồ và kiểm thử

Cấu hình `ddl-auto=update` hiện có tạo ba bảng mới khi khởi động. Với quản lý lược đồ thủ công, dùng [SQL](../src/main/resources/db/manual/20261112_order_management.sql) một lần. Không đổi trạng thái hoặc sửa giá các đơn cũ. Các liên kết mới dùng khóa ngoại để giữ toàn vẹn lịch sử khi có yêu cầu xóa cứng hóa đơn.

Kiểm thử trọng tâm: `OrderWorkflowTest` (gửi qua checkout hiện có, nhóm đơn thật, giá/tồn kho, quyền sở hữu, chuyển trạng thái, lịch sử, liên kết đánh giá/trả hàng), `OrderManagementSecurityTest` (HTTP/JSON, Admin, CSRF, lựa chọn trạng thái), cùng các kiểm thử order/checkout/voucher hiện có. Dữ liệu kiểm thử chạy trên H2 độc lập, không ghi MySQL ứng dụng.
