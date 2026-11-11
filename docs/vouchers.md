# Mã giảm giá LAPZONE

Quản trị tại `/admin/vouchers` (quyền ADMIN). Tạo/sửa/ngừng mã, xem số lượt còn lại,
đang giữ, đã dùng và lịch sử theo đơn/tài khoản. Không xóa lịch sử mã hoặc đơn đã gắn mã.

## Quy tắc

- Mã gồm 3–40 ký tự chữ/số, dấu gạch ngang hoặc gạch dưới; chuẩn hóa thành chữ hoa.
- Mức giảm 1–10%, số lượng tổng và giới hạn trên mỗi tài khoản; một mã mỗi đơn.
- Chỉ tài khoản đăng nhập được dùng mã. Danh sách tài khoản cho phép để trống nghĩa là mọi tài khoản.
- Điều kiện sản phẩm/danh mục là phép HOẶC; để trống cả hai nghĩa là mọi sản phẩm.
- Ngưỡng đơn tối thiểu tính trên toàn bộ giá trị hàng sau khuyến mãi sản phẩm.
- Chỉ phần hàng đủ điều kiện được giảm thêm. Tính bằng BigDecimal, làm tròn tiền giảm
  đến đồng (HALF_UP). Giá chi tiết vẫn là giá sau khuyến mãi sản phẩm; đơn lưu riêng
  tạm tính, mã và tiền giảm để đối soát. Tổng thanh toán và mã QR dùng tổng cuối từ backend.
- Thời gian quản trị dùng giờ Việt Nam; bắt đầu bao gồm, kết thúc không bao gồm.
- Sửa mã chỉ ảnh hưởng đơn mới. Không hạ tổng lượt dưới số lượt đang giữ + đã dùng.

## Vòng đời và đồng thời

Kiểm tra mã tại `POST /checkout/voucher-preview` chỉ đọc; không tạo lượt giữ.
Đặt hàng vẫn dùng CheckoutController/OrderService hiện có. Backend lấy chủ tài khoản từ
Spring Security, kiểm tra quyền sở hữu giỏ, tải giá từ database và tính lại toàn bộ.
Mã không hợp lệ bị từ chối toàn bộ đơn, không tự bỏ mã để thu số tiền cao hơn.

Trong cùng một transaction tạo đơn, khóa ghi dòng voucher trước khi kiểm tra hạn mức.
Số lượt chung được lưu trên dòng bị khóa; truy vấn hạn mức cá nhân dùng locking read
để có dữ liệu hiện tại cả dưới MySQL REPEATABLE READ. Chỉ tạo RESERVED sau khi đơn đã lưu.
Lỗi tồn kho/lưu chi tiết/unique constraint rollback cả đơn, tồn kho và lượt giữ.
Checkout key được cấp từ session; unique key toàn cục và unique order_id ngăn gửi lặp hoặc
gắn hai mã vào một đơn. POST quản trị và thanh toán có kiểm tra CSRF.

- Đơn mới (COD hoặc chuyển khoản): RESERVED, trừ số lượt còn lại nhưng chưa tính đã dùng.
- Xác nhận ĐÃ THANH TOÁN hoặc HOÀN THÀNH: chuyển đúng một lần sang REDEEMED.
- Hủy khi chưa đối soát thanh toán hoặc xác nhận thất bại lúc CHỜ THANH TOÁN:
  RELEASED, hoàn lượt và tồn kho đúng một lần. Lượt đã hoàn không tính vào hạn mức cá nhân.
- Hủy đơn đã REDEEMED giữ nguyên lượt đã dùng; không tự hoàn tiền hoặc cấp lại mã.
  Cần xử lý hoàn tiền riêng khi có quy trình đối soát thực tế.
- Đơn voucher đã hủy/thất bại/hoàn thành không được mở lại. Không sửa tổng tiền hoặc xóa đơn.
- Không tự giải phóng đơn chờ chuyển khoản theo thời gian: thanh toán có thể đến muộn.
  Admin phải đối soát rồi hủy/xác nhận thất bại. Hiện chưa có callback thanh toán;
  không thêm endpoint giả để tự nhận đã thanh toán.

## Cơ sở dữ liệu

Nếu sử dụng `ddl-auto=validate/none`, chạy một lần
[script MySQL](../src/main/resources/db/manual/20261111_vouchers.sql) trước triển khai.
Không chạy script lên schema đã được Hibernate tạo các bảng/cột này.
Cấu hình phát triển `ddl-auto=update` tạo bổ sung từ các entity.

Các bài kiểm tra giao dịch mặc định dùng H2 riêng, không chạm database ứng dụng.
Có thể chạy cùng bài kiểm tra trên một schema MySQL kiểm thử trống, riêng biệt, bằng:
`-Dspring.test.database.replace=NONE -Dspring.datasource.url=jdbc:mysql://localhost:3306/lapzone_voucher_test -Dspring.datasource.username=... -Dspring.datasource.password=...`.
Các bài kiểm tra dùng create-drop: schema kiểm thử sẽ được tạo lại và xóa khi kết thúc.
Không trỏ bộ kiểm tra vào database sản xuất.

## Tệp thay đổi cho hệ thống mã giảm giá

- [pom.xml](../pom.xml)
- [AdminVoucherController.java](../src/main/java/com/lapzone/lapzoneweb/controller/AdminVoucherController.java)
- [CheckoutController.java](../src/main/java/com/lapzone/lapzoneweb/controller/CheckoutController.java)
- [VoucherForm.java](../src/main/java/com/lapzone/lapzoneweb/model/dto/VoucherForm.java)
- [Order.java](../src/main/java/com/lapzone/lapzoneweb/model/entity/Order.java)
- [Voucher.java](../src/main/java/com/lapzone/lapzoneweb/model/entity/Voucher.java)
- [VoucherRedemption.java](../src/main/java/com/lapzone/lapzoneweb/model/entity/VoucherRedemption.java)
- [VoucherRepository.java](../src/main/java/com/lapzone/lapzoneweb/model/repository/VoucherRepository.java)
- [VoucherRedemptionRepository.java](../src/main/java/com/lapzone/lapzoneweb/model/repository/VoucherRedemptionRepository.java)
- [OrderService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/OrderService.java)
- [VoucherService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/VoucherService.java)
- [VoucherAdminService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/VoucherAdminService.java)
- [SecurityConfig.java](../src/main/java/com/lapzone/lapzoneweb/security/SecurityConfig.java)
- [20261111_vouchers.sql](../src/main/resources/db/manual/20261111_vouchers.sql)
- [checkout-voucher.js](../src/main/resources/static/js/checkout-voucher.js)
- [admin/orders.html](../src/main/resources/templates/admin/orders.html)
- [admin/sidebar.html](../src/main/resources/templates/admin/sidebar.html)
- [admin/vouchers.html](../src/main/resources/templates/admin/vouchers.html)
- [checkout.html](../src/main/resources/templates/checkout.html)
- [order_detail.html](../src/main/resources/templates/order_detail.html)
- [store.html](../src/main/resources/templates/store.html)
- [OrderServiceTest.java](../src/test/java/com/lapzone/lapzoneweb/OrderServiceTest.java)
- [VoucherAdminServiceTest.java](../src/test/java/com/lapzone/lapzoneweb/VoucherAdminServiceTest.java)
- [VoucherSecurityTest.java](../src/test/java/com/lapzone/lapzoneweb/VoucherSecurityTest.java)
- [VoucherTransactionTest.java](../src/test/java/com/lapzone/lapzoneweb/VoucherTransactionTest.java)
- [vouchers.md](./vouchers.md)
