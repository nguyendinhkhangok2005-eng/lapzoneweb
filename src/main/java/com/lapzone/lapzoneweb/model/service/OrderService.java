package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.OrderDetail;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import com.lapzone.lapzoneweb.model.repository.OrderDetailRepository;
import com.lapzone.lapzoneweb.model.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    // ====================================================================
    // CỔNG 1: DÀNH CHO LUỒNG THANH TOÁN TỪ GIỎ HÀNG (Sử dụng List ID)
    // ====================================================================
    @Transactional
    public Order placeOrder(User user, String customerName, String customerPhone, String address, List<Long> selectedItemIds, String paymentMethod) {
        List<CartItem> selectedItems = cartItemRepository.findAllById(selectedItemIds);
        if (selectedItems.isEmpty()) {
            throw new RuntimeException("Chưa chọn sản phẩm nào để thanh toán!");
        }
        // Gọi hàm lõi và báo là "Đến từ giỏ hàng" (để còn xóa khỏi giỏ)
        return createOrderCore(user, customerName, customerPhone, address, selectedItems, paymentMethod, true);
    }

    // ====================================================================
    // CỔNG 2: DÀNH CHO LUỒNG MUA NGAY & KHÁCH VÃNG LAI (Dùng CartItem tạm)
    // ====================================================================
    @Transactional
    public Order placeOrderBuyNow(User user, String customerName, String customerPhone, String address, CartItem buyNowItem, String paymentMethod) {
        // Gọi hàm lõi và báo là "Không đến từ giỏ hàng"
        return createOrderCore(user, customerName, customerPhone, address, List.of(buyNowItem), paymentMethod, false);
    }


    // ====================================================================
    // HÀM LÕI: XỬ LÝ CHUNG CHO CẢ 2 LUỒNG
    // ====================================================================
    private Order createOrderCore(User user, String customerName, String customerPhone, String address, List<CartItem> items, String paymentMethod, boolean isFromCart) {
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
            totalAmount += item.getProduct().getPrice() * item.getQuantity();
        }
        newOrder.setTotalAmount(totalAmount);

        // Lưu Hóa đơn
        Order savedOrder = orderRepository.save(newOrder);

        // Lưu Chi tiết hóa đơn
        for (CartItem item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setOrder(savedOrder);
            detail.setProduct(item.getProduct());
            detail.setQuantity(item.getQuantity());
            detail.setPrice(item.getProduct().getPrice()); 
            orderDetailRepository.save(detail);

            // Xóa món hàng khỏi DB giỏ hàng CHỈ KHI nó xuất phát từ giỏ hàng
            if (isFromCart && item.getId() != null) {
                cartItemRepository.delete(item);
            }
        }

        return savedOrder;
    }
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số: #" + orderId));
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
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
            order.setStatus(newStatus);
            orderRepository.save(order);
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
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số #" + orderId));
                
                order.setCustomerName(name);
                order.setCustomerPhone(phone);
                order.setAddress(address);
                order.setTotalAmount(totalAmount);
                order.setStatus(status);
                
                orderRepository.save(order);
            }

            // ====================================================================
            // CHỨC NĂNG XÓA HÓA ĐƠN (Xóa chi tiết trước rồi xóa hóa đơn sau)
            // ====================================================================
            @Transactional
            public void deleteInvoiceAndDetails(Long orderId) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn số #" + orderId));
                
                // Nếu hóa đơn có danh sách chi tiết sản phẩm, xóa sạch chúng trong DB trước để tránh lỗi ràng buộc khóa ngoại (Foreign Key)
                if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
                    orderDetailRepository.deleteAll(order.getOrderDetails());
                }
                
                // Sau đó tiến hành xóa hóa đơn chính
                orderRepository.delete(order);
            }
}