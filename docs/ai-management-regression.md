# LAPZONE AI Management — kiểm tra hồi quy cuối

Ngày: 13/11/2026. Giữ nguyên toàn bộ thay đổi có sẵn; chỉ sửa các vấn đề hiệu năng xác nhận trong lần rà soát này.

## Kết quả thực chạy

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ Maven, sau sửa: `mvn -q package` với datasource H2 riêng, `MODE=MySQL`, schema `create-drop` | **294 đạt, 0 thất bại, 0 lỗi, 8 bỏ qua**; 302 test case trong 51 lớp |
| JavaScript: `node --test src/test/js/*.test.cjs` | **19 đạt, 0 thất bại** |
| Kiểm tra khoảng trắng: `git diff --check` | Đạt |
| Đóng gói Spring Boot JAR | Thành công |

8 test bị bỏ qua do điều kiện opt-in có sẵn: 3 trong `CartRemovalMySqlTest`, 5 trong `CustomerOrderMySqlReadOnlyTest`. Không bật cờ MySQL, không sửa database thật. AI HTTP transport được giả lập trong các test sản phẩm; các test chuyển hỗ trợ dùng controller/security/service và SQL thật trên H2, không gọi nhà cung cấp AI.

## Phạm vi xác minh

| # | Hạng mục | Bằng chứng / giới hạn |
|---|---|---|
| 1–3 | Sản phẩm DB, ID, giá, ảnh, cấu hình, tồn kho, ngân sách/yêu cầu | Các test recommendation/catalog/validator đạt; kiểm tra ID sai, ảnh sai, ngân sách, tồn kho, cấu hình và thay đổi dữ liệu sau khi AI chọn. Dùng bản ghi SQL của fixture H2; chưa đối chiếu danh mục MySQL đang vận hành. Bộ tách yêu cầu chỉ hỗ trợ các mẫu đã nhận diện, không chứng minh mọi cách diễn đạt tự nhiên. |
| 4 | Dashboard số liệu thật | Aggregate theo ngày/khoảng thời gian, phân trang, feedback và N/A đã được test; danh mục được hỏi nhiều vẫn N/A vì chưa được theo dõi có cấu trúc. |
| 5 | Đánh giá an toàn | Quyền sở hữu, CSRF, lý do tiếng Việt, tính bất biến, gửi trùng và báo cáo hạn chế nội dung đều có test đạt. |
| 6 | Kiến thức đã xuất bản | Draft, xuất bản, chỉnh sửa, bỏ xuất bản, lưu trữ, phiên bản và phản hồi sau cập nhật đều có test đạt. |
| 7 | Cấu hình Admin | Validation, lưu/reset, audit, preview và ảnh hưởng tới phản hồi tiếp theo có test đạt; không có trường API key hoặc tắt bảo mật. |
| 8 | Kiểm tra chất lượng gợi ý | PASS/FAIL/NOT VERIFIED được test; chất lượng chủ quan, thiếu dữ liệu và yêu cầu chưa nhận diện không được coi là đã xác minh. Lịch sử kiểm tra tách khỏi chat khách. |
| 9 | Chuyển hỗ trợ | Luồng xem trước → đồng ý → tạo yêu cầu → Admin trả lời/đóng → khách đọc trạng thái đạt; test quyền sở hữu, lựa chọn ngữ cảnh, CSRF, gửi đồng thời và giữ yêu cầu khi xóa AI history đạt. |
| 10–11 | ROLE_ADMIN và riêng tư | Tất cả controller `/admin/ai/**` có kiểm tra ROLE_ADMIN và bảo vệ route; các test khách/owner khác/Admin/guest đạt. Báo cáo Admin không lấy toàn bộ nội dung AI riêng tư. |
| 12 | New Chat, lịch sử, giao diện | Backend tạo/đổi tên/reset/xóa, lịch sử độc lập và card hiện hành đạt. JavaScript parse, thẻ sản phẩm, fallback ảnh, ưu đãi, feedback và handoff đạt. Themes/fullscreen/New Chat DOM được rà soát mã, **chưa chạy thao tác hoặc kiểm tra hình ảnh trong trình duyệt thật**. |
| 13 | Checkout và quản lý sản phẩm | Các test checkout/cart/order/voucher hiện có đạt. Không sửa controller quản lý sản phẩm, checkout hoặc order service trong lần này. **Chưa kiểm thử đầy đủ CRUD sản phẩm/upload ảnh qua trình duyệt**; không có suite riêng bao trùm toàn bộ luồng này. |
| 14 | SQL, bộ nhớ, API | Đã sửa tải toàn bộ entity danh mục, query lặp theo từng tin nhắn lịch sử và lookup FAQ trùng trước khi trả lời. Test đo SQL xác nhận 105 sản phẩm dùng 2 lô + 1 query xác thực, 0 entity danh mục được load; lịch sử 3 lần gợi ý dùng 1 query thẻ chung. Test xác nhận 1 AI request ở đường thành công; retry/fallback có giới hạn, inspector chỉ chạy do Admin yêu cầu và có chống trùng. Chưa benchmark tải thực tế. |

## Tệp thay đổi trong lần rà soát này

Mã chạy:

- [ProductRepository.java](../src/main/java/com/lapzone/lapzoneweb/model/repository/ProductRepository.java): đọc ứng viên bằng scalar projection, keyset theo ID, 100 bản ghi mỗi lô; không join gallery ở bước tìm ứng viên.
- [AiConsultantService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/AiConsultantService.java): chỉ giữ 8 ứng viên tốt nhất, vẫn xét toàn bộ danh mục; bỏ lookup FAQ lặp trước khi trả lời. Vẫn đọc lại kiến thức sau AI call để tránh dùng bản vừa bị bỏ xuất bản.
- [AiConversationService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/AiConversationService.java): gom ID sản phẩm trùng trong lịch sử/ngữ cảnh và nạp card theo lô tối đa 100 ID; giữ thứ tự và cập nhật dữ kiện từ DB.

Kiểm thử và báo cáo:

- [AiProductCatalogIntegrationTest.java](../src/test/java/com/lapzone/lapzoneweb/AiProductCatalogIntegrationTest.java): thêm 2 test hiệu năng SQL/bộ nhớ nêu trên.
- [AiCatalogFixture.java](../src/test/java/com/lapzone/lapzoneweb/AiCatalogFixture.java): fixture phân lô dùng chung.
- [AiConsultantServiceTest.java](../src/test/java/com/lapzone/lapzoneweb/AiConsultantServiceTest.java), [AiProductRecommendationTest.java](../src/test/java/com/lapzone/lapzoneweb/AiProductRecommendationTest.java), [AiResponseSettingsConsultationTest.java](../src/test/java/com/lapzone/lapzoneweb/AiResponseSettingsConsultationTest.java): điều chỉnh fixture sang query phân lô; giữ các assertion hành vi.
- [AiSupportPolicyTest.java](../src/test/java/com/lapzone/lapzoneweb/AiSupportPolicyTest.java): cập nhật query và kiểm tra FAQ chỉ được lookup một lần trên đường trả lời trực tiếp.
- Báo cáo này.

## Giới hạn còn lại

- Chưa chạy MySQL thật, migration SQL thủ công, API AI thật hoặc kiểm tra tồn tại/nội dung của file ảnh thực tế. Tính đúng của ảnh được đối chiếu bằng quan hệ/path trong DB.
- Danh mục vẫn được quét theo lô để không bỏ sót sản phẩm do bộ lọc cấu hình văn bản; bộ nhớ ứng viên có giới hạn nhưng thời gian vẫn tăng theo số sản phẩm.
- API mở một hội thoại vẫn trả toàn bộ lịch sử của hội thoại đó; chưa có phân trang tin nhắn AI hoặc benchmark hội thoại rất dài. Ngữ cảnh gửi AI vẫn giới hạn 12 tin gần nhất.
- Chưa xác minh toàn bộ thao tác themes/fullscreen/mobile và CRUD sản phẩm trên trình duyệt; không coi các phần này là đã có kiểm thử end-to-end trực quan.
