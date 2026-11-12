# Đối chiếu quyền sở hữu đơn hàng — 12/11/2026

## Kết quả MySQL thật

Đã đọc `lapzone_db` qua MySQL Shell trong giao dịch `READ ONLY`, sau đó đối chiếu bằng kiểm thử tích hợp chỉ đọc. Không cập nhật bản ghi, lược đồ, trạng thái, tồn kho hoặc quyền sở hữu.

| Đơn | Phương thức | Trạng thái gốc | `user_id` | Kết luận |
| --- | --- | --- | --- | --- |
| #1 | COD | CHỜ XÁC NHẬN | NULL | Đơn khách vãng lai/chưa liên kết tài khoản; tên người nhận không chứng minh chủ sở hữu |
| #7 | VNPAY | ĐÃ THANH TOÁN | 1 | Thuộc tài khoản ID 1; có trong kết quả khách hàng của ID 1 |

Sáu đơn có `customer_name = 'Nguyen Dinh Khang'` là **#1, #2, #5, #6, #7, #9**. Trong đó chỉ **#2, #5, #6, #7, #9** có `user_id = 1`; #1 có `user_id = NULL`.

Database có hai tài khoản cùng tên:

| Tài khoản | Vai trò hiện tại | Đơn thực sự gắn với tài khoản |
| --- | --- | --- |
| ID 1 | ADMIN | #2, #5, #6, #7, #9 |
| ID 2 | USER | Không có |

Vì thế số lượng sáu theo tên người nhận ở Admin không tương đương sáu đơn thuộc một tài khoản. Số lượng năm ở trang khách hàng khớp dữ liệu sở hữu của ID 1. Không được gộp hai tài khoản hay nhận đơn khách vãng lai chỉ vì trùng tên/SĐT.

## Truy vết đến trang khách hàng

- [Order](../src/main/java/com/lapzone/lapzoneweb/model/entity/Order.java) ánh xạ quan hệ `user` qua khóa ngoại `orders.user_id`; `customerName` chỉ là thông tin người nhận.
- [User](../src/main/java/com/lapzone/lapzoneweb/model/entity/User.java) dùng ID làm định danh. Tên không duy nhất.
- [Xác thực đăng nhập](../src/main/java/com/lapzone/lapzoneweb/security/CustomUserDetailsService.java) lấy tài khoản bằng email/SĐT đăng nhập; [kiểm tra quyền đơn](../src/main/java/com/lapzone/lapzoneweb/model/service/OrderAuthorization.java) lấy ID từ Spring Security principal và kiểm tra tài khoản còn tồn tại trong DB.
- [OrderRepository](../src/main/java/com/lapzone/lapzoneweb/model/repository/OrderRepository.java) lọc `o.user.id = :userId`. Với `category=ALL`, không lọc theo trạng thái, tên, phương thức hoặc yêu cầu có dòng sản phẩm. Truy vấn hiện tại đúng với dữ liệu đã kiểm tra; không sửa truy vấn hay quan hệ để nới quyền.
- [CustomerOrderService](../src/main/java/com/lapzone/lapzoneweb/model/service/CustomerOrderService.java) truyền đúng ID được xác thực, giữ tổng số và phân trang; [controller](../src/main/java/com/lapzone/lapzoneweb/controller/CustomerOrdersPageController.java) chuyển kết quả đó sang Thymeleaf.
- Với principal kiểm thử của **tài khoản thật ID 1**, SQL, dịch vụ, API và model trang `/store/orders?category=ALL` đều trả **[9, 7, 6, 5, 2]**. ID 2 trả **[]**. #1 và đơn của tài khoản khác trả 404 qua trang/API khách hàng, kể cả khi principal có vai trò ADMIN; đường dẫn Admin vẫn có quyền đọc riêng như trước.

Không có phiên trình duyệt đăng nhập được chia sẻ và không có server HTTP đang lắng nghe ở các cổng đã kiểm tra. Vì vậy đây là đối chiếu bằng principal kiểm thử lấy từ tài khoản MySQL thật, không phải xác nhận trực tiếp cookie/ID trong trình duyệt của người dùng. Kết quả năm đơn được báo cáo khớp ID 1.

## Bản sửa tối thiểu

Đã tái hiện một lỗi checkout độc lập: khi Spring Security đã xác thực nhưng thiếu `session.currentUser`, checkout Mua ngay truyền `null` làm chủ đơn. Thuộc tính session cũ lệch sang tài khoản khác cũng có thể gán sai chủ đơn.

[CheckoutController](../src/main/java/com/lapzone/lapzoneweb/controller/CheckoutController.java) nay lấy chủ đơn từ principal xác thực cho trang checkout và thao tác tạo đơn; không tin thuộc tính session cũ để gán tài khoản. Khách chưa xác thực vẫn có thể Mua ngay với chủ đơn `null`. Không đổi tạo đơn, tính tiền, voucher, trạng thái thanh toán, chuyển trạng thái Admin hay dữ liệu lịch sử.

Không có log lịch sử đủ để kết luận lỗi session này đã gây ra #1. Bản sửa ngăn lỗi đó tái diễn, không phải bằng chứng để gán lại #1.

**#1 vẫn chưa được liên kết và không xuất hiện trong lịch sử tài khoản.** Chỉ được liên kết sau quy trình xác minh bằng chứng sở hữu đáng tin cậy (ví dụ chứng từ được đối soát hoặc token gắn với phiên checkout gốc). Hiện không có bằng chứng đó trong yêu cầu hoặc bản ghi đã kiểm tra. Không tạo luồng nhận đơn bằng tên/SĐT, không nhân bản đơn, không tự sửa `user_id`.

## Kiểm thử đã chạy

- Trước sửa: kiểm thử mới `authenticatedBuyNowWithoutLegacySessionStillOwnsTheOrder` thất bại, xác nhận checkout truyền chủ đơn `null` dù principal đã xác thực.
- Sau sửa: **38 kiểm thử đạt, 0 lỗi, 0 bỏ qua**: `CheckoutControllerTest` (9), `VoucherSecurityTest` (7), `OrderWorkflowTest` (9), `OrderServiceOwnershipTest` (5), `CustomerOrdersPageTest` (8).
- MySQL thật: **2 kiểm thử đạt, 0 lỗi, 0 bỏ qua** trong [CustomerOrderMySqlReadOnlyTest](../src/test/java/com/lapzone/lapzoneweb/CustomerOrderMySqlReadOnlyTest.java). Kiểm thử ép connection/giao dịch chỉ đọc, `ddl-auto=none`, xác nhận `@@transaction_read_only = 1`, không tạo fixture. So sánh toàn bộ ID sở hữu với dịch vụ/API/model trang, kiểm tra khách vãng lai và tài khoản khác bị từ chối.
- Tổng cộng **40 kiểm thử sau sửa đạt**. Không chạy lại toàn bộ suite hoặc kiểm thử trình duyệt trong lần sửa này.

Lệnh đối chiếu MySQL (dùng cấu hình database hiện có, không in mật khẩu):

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' `
  '-Dtest=CustomerOrderMySqlReadOnlyTest' '-Dlapzone.test.mysql-read-only=true' `
  '-Dlapzone.test.customer-ids=1,2' test
```

Tệp thay đổi trong lần này: [CheckoutController](../src/main/java/com/lapzone/lapzoneweb/controller/CheckoutController.java), [CheckoutControllerTest](../src/test/java/com/lapzone/lapzoneweb/CheckoutControllerTest.java), [CustomerOrderMySqlReadOnlyTest](../src/test/java/com/lapzone/lapzoneweb/CustomerOrderMySqlReadOnlyTest.java) và tài liệu này. Không thay đổi luồng Admin hoặc hồ sơ #1/#7.
