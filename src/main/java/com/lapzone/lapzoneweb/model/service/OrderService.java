package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.OrderDetail;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import com.lapzone.lapzoneweb.model.repository.OrderDetailRepository;
import com.lapzone.lapzoneweb.model.repository.OrderRepository;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final String CANCELLED_STATUS = "ĐÃ HỦY";

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VoucherService voucherService;

    // ====================================================================
    // CỔNG 1: DÀNH CHO LUỒNG THANH TOÁN TỪ GIỎ HÀNG (Sử dụng List ID)
    // ====================================================================
    @Transactional
    public Order placeOrder(User user, String customerName, String customerPhone, String address, List<Long> selectedItemIds, String paymentMethod) {
        return placeOrder(user, customerName, customerPhone, address, selectedItemIds, paymentMethod, null, null);
    }

    @Transactional
    public Order placeOrder(User user, String customerName, String customerPhone, String address, List<Long> selectedItemIds, String paymentMethod, String voucherCode, String checkoutKey) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để thanh toán giỏ hàng.");
        }
        if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm để thanh toán.");
        }
        List<CartItem> selectedItems = cartItemRepository.findByIdInAndUserIdWithProduct(selectedItemIds, user.getId());
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy sản phẩm hợp lệ trong giỏ hàng của bạn.");
        }
        // Gọi hàm lõi và báo là "Đến từ giỏ hàng" (để còn xóa khỏi giỏ)
        return createOrderCore(user, customerName, customerPhone, address, selectedItems, paymentMethod, true, voucherCode, checkoutKey);
    }

    // ====================================================================
    // CỔNG 2: DÀNH CHO LUỒNG MUA NGAY & KHÁCH VÃNG LAI (Dùng CartItem tạm)
    // ====================================================================
    @Transactional
    public Order placeOrderBuyNow(User user, String customerName, String customerPhone, String address, CartItem buyNowItem, String paymentMethod) {
        return placeOrderBuyNow(user, customerName, customerPhone, address, buyNowItem, paymentMethod, null, null);
    }

    @Transactional
    public Order placeOrderBuyNow(User user, String customerName, String customerPhone, String address, CartItem buyNowItem, String paymentMethod, String voucherCode, String checkoutKey) {
        if (buyNowItem == null || buyNowItem.getProduct() == null || buyNowItem.getProduct().getId() == null) {
            throw new IllegalArgumentException("Không tìm thấy sản phẩm mua ngay.");
        }
        Integer quantity = buyNowItem.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Số lượng mua ngay phải lớn hơn 0.");
        }
        Product product = productRepository.findById(buyNowItem.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm mua ngay không còn tồn tại."));
        if (product.getPrice() == null) {
            throw new IllegalArgumentException("Sản phẩm hiện không có giá hợp lệ.");
        }
        if (product.getStock() == null || quantity > product.getStock()) {
            throw new IllegalArgumentException("Số lượng mua vượt quá tồn kho hiện có.");
        }

        CartItem trustedItem = new CartItem();
        trustedItem.setProduct(product);
        trustedItem.setQuantity(quantity);
        // Gọi hàm lõi và báo là "Không đến từ giỏ hàng"
        return createOrderCore(user, customerName, customerPhone, address, List.of(trustedItem), paymentMethod, false, voucherCode, checkoutKey);
    }


    // ====================================================================
    // HÀM LÕI: XỬ LÝ CHUNG CHO CẢ 2 LUỒNG
    // ====================================================================
    private Order createOrderCore(User user, String customerName, String customerPhone, String address, List<CartItem> items, String paymentMethod, boolean isFromCart, String voucherCode, String checkoutKey) {
        VoucherService.Prepared voucher = voucherCode == null || voucherCode.isBlank() ? null
                : voucherService.prepare(voucherCode, user, items, checkoutKey);
        if (voucher != null && !java.util.Set.of("COD", "VNPAY", "MOMO").contains(paymentMethod))
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        for (CartItem item : items) {
            Product product = item.getProduct();
            Integer quantity = item.getQuantity();
            if (product == null || product.getId() == null || quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Sản phẩm hoặc số lượng đặt hàng không hợp lệ.");
            }
            if (productRepository.decrementStockIfAvailable(product.getId(), quantity) == 0) {
                throw new InsufficientStockException(
                        "Sản phẩm \"" + product.getName() + "\" không đủ tồn kho cho số lượng yêu cầu (x" + quantity + ").");
            }
        }

        Order newOrder = new Order();
        newOrder.setUser(user); // Khách vãng lai user sẽ là null, DB tự hiểu
        newOrder.setCustomerName(customerName);
        newOrder.setCustomerPhone(customerPhone);
        newOrder.setAddress(address);
        newOrder.setOrderDate(new Date());
        newOrder.setPaymentMethod(paymentMethod);
        
        // Thiết lập trạng thái NGAY TRƯỚC KHI LƯU
        if (!"COD".equals(paymentMethod)) {
            newOrder.setStatus("CHỜ THANH TOÁN");
        } else {
            newOrder.setStatus("CHỜ XÁC NHẬN");
        }

        // Tính tổng tiền
        double totalAmount = 0;
        for(CartItem item : items) {
            totalAmount += PromotionService.getDiscountedPrice(item.getProduct()) * item.getQuantity();
        }
        newOrder.setTotalAmount(totalAmount);
        if (voucher != null) {
            newOrder.setVoucherCode(voucher.voucher().getCode());
            newOrder.setSubtotalBeforeVoucher(voucher.quote().subtotal());
            newOrder.setVoucherDiscount(voucher.quote().discount());
            newOrder.setTotalAmount(voucher.quote().total().doubleValue());
        }

        // Lưu Hóa đơn
        Order savedOrder = orderRepository.save(newOrder);
        if (voucher != null) voucherService.reserve(voucher, user, savedOrder, checkoutKey);

        // Lưu Chi tiết hóa đơn
        for (CartItem item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setOrder(savedOrder);
            detail.setProduct(item.getProduct());
            detail.setQuantity(item.getQuantity());
            detail.setPrice(voucher == null ? PromotionService.getDiscountedPrice(item.getProduct())
                    : voucher.quote().prices().get(item.getProduct().getId()).doubleValue());
            orderDetailRepository.save(detail);

            // Xóa món hàng khỏi DB giỏ hàng CHỈ KHI nó xuất phát từ giỏ hàng
            if (isFromCart && item.getId() != null) {
                cartItemRepository.deleteByIdAndUserId(item.getId(), user.getId());
            }
        }

        return savedOrder;
    }
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số: #" + orderId));
    }

    public Optional<Order> findOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    // Hàm tìm kiếm và lọc đơn hàng - [CŨ] Chỉ dùng cho export/in báo cáo
        public List<Order> searchOrders(String keyword, String status, java.util.Date startDate, java.util.Date endDate) {
            if (status != null && status.isEmpty()) status = null;
            if (keyword != null && keyword.isEmpty()) keyword = null;
            return orderRepository.searchOrders(keyword, status, startDate, endDate);
        }

        // [MỚI - FIX HIỆU SUẤT] Tìm kiếm có phân trang - CHỈ load 20 bản ghi/trang
        public Page<Order> searchOrdersPaged(String keyword, String status,
                                             java.util.Date startDate, java.util.Date endDate,
                                             int page, int size) {
            if (status != null && status.isEmpty()) status = null;
            if (keyword != null && keyword.isEmpty()) keyword = null;
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
            return orderRepository.searchOrdersPaged(keyword, status, startDate, endDate, pageable);
        }

        // Hàm cập nhật trạng thái
        @Transactional
        public void updateOrderStatus(Long orderId, String newStatus) {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
            updateStatusAndRestock(order, newStatus);
        }

        private void updateStatusAndRestock(Order order, String newStatus) {
            boolean voucherOrder = order.getVoucherCode() != null;
            if (voucherOrder) {
                if (!java.util.Set.of("CHỜ THANH TOÁN", "CHỜ XÁC NHẬN", "ĐANG ĐÓNG GÓI", "ĐANG GIAO HÀNG", "ĐÃ THANH TOÁN", "HOÀN THÀNH", CANCELLED_STATUS, "THANH TOÁN THẤT BẠI").contains(newStatus))
                    throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ.");
                if (java.util.Set.of(CANCELLED_STATUS, "THANH TOÁN THẤT BẠI", "HOÀN THÀNH").contains(order.getStatus()) && !order.getStatus().equals(newStatus))
                    throw new IllegalArgumentException("Không thể mở lại đơn hàng đã kết thúc.");
                if ("THANH TOÁN THẤT BẠI".equals(newStatus) && !"CHỜ THANH TOÁN".equals(order.getStatus()) && !newStatus.equals(order.getStatus()))
                    throw new IllegalArgumentException("Chỉ đơn chờ thanh toán được xác nhận thanh toán thất bại.");
                voucherService.onOrderStatus(order, newStatus);
            }
            boolean wasClosed = CANCELLED_STATUS.equals(order.getStatus()) || "THANH TOÁN THẤT BẠI".equals(order.getStatus());
            boolean shouldRestock = !wasClosed && (CANCELLED_STATUS.equals(newStatus)
                    || (voucherOrder && "THANH TOÁN THẤT BẠI".equals(newStatus)));
            order.setStatus(newStatus);
            orderRepository.save(order);

            if (shouldRestock) {
                for (OrderDetail detail : orderDetailRepository.findByOrderId(order.getId())) {
                    Product product = detail.getProduct();
                    int updatedRows = productRepository.incrementStock(product.getId(), detail.getQuantity());
                    if (updatedRows == 0) {
                        throw new IllegalStateException(
                                "Không thể hoàn tồn kho cho sản phẩm \"" + product.getName() + "\".");
                    }
                }
            }
        }
        // [C\u0168 - gi\u1eef cho export/in b\u00e1o c\u00e1o]
        public List<Order> getValidInvoices(java.util.Date startDate, java.util.Date endDate) {
            return orderRepository.findValidInvoicesBetweenDates(startDate, endDate);
        }

        // [M\u1edaI - FIX HI\u1ec6U SU\u1ea4T] Ph\u00e2n trang: ch\u1ec9 load 20 h\u00f3a \u0111\u01a1n/trang cho trang danh s\u00e1ch
        public Page<Order> getValidInvoicesPaged(java.util.Date startDate, java.util.Date endDate,
                                                  int page, int size) {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
            return orderRepository.findValidInvoicesPaged(startDate, endDate, pageable);
        }

        // Lấy doanh thu tháng này
        public Double getCurrentMonthRevenue() {
            Double revenue = orderRepository.calculateCurrentMonthRevenue();
            return revenue != null ? revenue : 0.0; // Tránh lỗi nếu tháng này chưa bán được gì
        }
        // ====================================================================
            // CHỨC NĂNG SỬA HÓA ĐƠN
            // ====================================================================
            @Transactional
            public void updateInvoiceDetails(Long orderId, String name, String phone, String address, Double totalAmount, String status) {
                Order order = orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số #" + orderId));
                
                order.setCustomerName(name);
                order.setCustomerPhone(phone);
                order.setAddress(address);
                if (order.getVoucherCode() != null && !java.util.Objects.equals(order.getTotalAmount(), totalAmount))
                    throw new IllegalArgumentException("Không được sửa tổng tiền của đơn có mã giảm giá.");
                order.setTotalAmount(totalAmount);
                updateStatusAndRestock(order, status);
            }

            // ====================================================================
            // CHỨC NĂNG XÓA HÓA ĐƠN (Xóa chi tiết trước rồi xóa hóa đơn sau)
            // ====================================================================
            @Transactional
            public void deleteInvoiceAndDetails(Long orderId) {
                Order order = orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số #" + orderId));
                if (order.getVoucherCode() != null)
                    throw new IllegalArgumentException("Không được xóa đơn có lịch sử mã giảm giá. Hãy hủy đơn nếu cần.");
                
                // Nếu hóa đơn có danh sách chi tiết sản phẩm, xóa sạch chúng trong DB trước để tránh lỗi ràng buộc khóa ngoại (Foreign Key)
                if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
                    orderDetailRepository.deleteAll(order.getOrderDetails());
                }
                
                // Sau đó tiến hành xóa hóa đơn chính
                orderRepository.delete(order);
            }
}
