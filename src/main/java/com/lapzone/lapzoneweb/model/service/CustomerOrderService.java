package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CustomerOrderService {
    private final OrderRepository orders;
    private final OrderDetailRepository details;
    private final OrderItemReviewRepository reviews;
    private final OrderReturnCaseRepository returns;
    private final OrderStatusChangeRepository history;
    private final OrderAuthorization authorization;

    public CustomerOrderService(OrderRepository orders, OrderDetailRepository details, OrderItemReviewRepository reviews,
                                OrderReturnCaseRepository returns, OrderStatusChangeRepository history, OrderAuthorization authorization) {
        this.orders = orders; this.details = details; this.reviews = reviews; this.returns = returns;
        this.history = history; this.authorization = authorization;
    }
    public record CategoryView(String key, String label) {}
    public record ReviewView(Long id, int rating, String content, LocalDateTime createdAt) {}
    public record ReturnView(Long id, Long orderDetailId, String type, String status, String reason, LocalDateTime createdAt,
                             Integer quantity, String adminNote, LocalDateTime reviewedAt, String completionNote, LocalDateTime completedAt) {
        public ReturnView(Long id, Long orderDetailId, String type, String status, String reason, LocalDateTime createdAt) {
            this(id, orderDetailId, type, status, reason, createdAt, null, null, null, null, null);
        }
    }
    public record ItemView(Long id, Long productId, String productName, String productImage, Integer quantity,
                           Double unitPrice, BigDecimal lineTotal, boolean needsReview, ReviewView review, List<Long> returnCaseIds,
                           boolean repurchaseAvailable) {
        public ItemView(Long id, Long productId, String productName, String productImage, Integer quantity,
                        Double unitPrice, BigDecimal lineTotal, boolean needsReview, ReviewView review, List<Long> returnCaseIds) {
            this(id, productId, productName, productImage, quantity, unitPrice, lineTotal, needsReview, review, returnCaseIds, false);
        }
    }
    public record OrderView(Long id, Instant orderDate, String status, String paymentMethod, Double totalAmount,
                            String voucherCode, BigDecimal voucherDiscount, BigDecimal subtotalBeforeVoucher,
                            String customerName, String customerPhone, String address,
                            List<CategoryView> categories, List<ItemView> items, List<ReturnView> returnCases) {}
    public record HistoryView(String previousStatus, String newStatus, LocalDateTime changedAt) {}
    public record DetailView(OrderView order, List<HistoryView> statusHistory) {}
    public record OrderPage(List<OrderView> content, int page, int size, long totalElements, int totalPages, CategoryView category,
                            Map<String, Long> categoryCounts) {
        public OrderPage(List<OrderView> content, int page, int size, long totalElements, int totalPages, CategoryView category) {
            this(content, page, size, totalElements, totalPages, category, Map.of());
        }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderPage list(String category, int page, int size) {
        User user = authorization.requireUser();
        CustomerOrderCategory group = CustomerOrderCategory.parse(category);
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw new IllegalArgumentException("Thông tin phân trang không hợp lệ.");
        var result = orders.customerOrders(user.getId(), group.name(), OrderStatusPolicy.PENDING_STATUSES,
                OrderStatusPolicy.SHIPPING_STATUSES, OrderStatusPolicy.DELIVERED_STATUSES, OrderStatusPolicy.CANCELLED,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("id"))));
        return new OrderPage(views(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), category(group), counts(user.getId(), group, result.getTotalElements()));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Long> categoryCounts() {
        return counts(authorization.requireUser().getId(), null, 0);
    }

    private Map<String, Long> counts(Long ownerId, CustomerOrderCategory selected, long selectedTotal) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (CustomerOrderCategory group : CustomerOrderCategory.values())
            result.put(group.name(), group == selected ? selectedTotal : orders.countCustomerOrders(ownerId, group.name(),
                    OrderStatusPolicy.PENDING_STATUSES, OrderStatusPolicy.SHIPPING_STATUSES,
                    OrderStatusPolicy.DELIVERED_STATUSES, OrderStatusPolicy.CANCELLED));
        return Collections.unmodifiableMap(result);
    }

    public DetailView detail(Long id) {
        User user = authorization.requireUser();
        Order order = orders.findByIdAndUserId(id, user.getId()).orElseThrow(this::missing);
        return detailView(order);
    }

    public DetailView adminDetail(Long id) {
        authorization.requireAdmin();
        return detailView(orders.findById(id).orElseThrow(this::missing));
    }

    public ItemView item(Long orderId, Long itemId) {
        return detail(orderId).order().items().stream().filter(item -> item.id().equals(itemId)).findFirst().orElseThrow(this::missing);
    }

    private DetailView detailView(Order order) {
        var changes = history.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(change -> new HistoryView(change.getPreviousStatus(), change.getNewStatus(), change.getChangedAt())).toList();
        return new DetailView(views(List.of(order)).getFirst(), changes);
    }

    private List<OrderView> views(List<Order> selected) {
        if (selected.isEmpty()) return List.of();
        List<Long> ids = selected.stream().map(order -> order.getId()).toList();
        var lines = details.findByOrderIdIn(ids).stream().collect(Collectors.groupingBy(line -> line.getOrder().getId()));
        var byLine = reviews.findByOrderDetailOrderIdIn(ids).stream().collect(Collectors.toMap(
                review -> review.getOrderDetail().getId(), review -> review));
        var cases = returns.findByOrderIdIn(ids).stream().collect(Collectors.groupingBy(c -> c.getOrder().getId()));
        return selected.stream().map(order -> {
            String status = order.getStatus() == null ? "" : order.getStatus();
            var orderCases = cases.getOrDefault(order.getId(), List.of()).stream().sorted(Comparator.comparing(c -> c.getId()))
                    .map(c -> new ReturnView(c.getId(), c.getOrderDetail() == null ? null : c.getOrderDetail().getId(),
                            c.getType().name(), c.getStatus().name(), c.getReason(), c.getCreatedAt(), c.getQuantity(),
                            c.getAdminNote(), c.getReviewedAt(), c.getCompletionNote(), c.getCompletedAt())).toList();
            var items = lines.getOrDefault(order.getId(), List.of()).stream().sorted(Comparator.comparing(line -> line.getId())).map(line -> {
                Product product = line.getProduct();
                OrderItemReview review = byLine.get(line.getId());
                ReviewView reviewView = review == null ? null : new ReviewView(review.getId(), review.getRating(), review.getContent(), review.getCreatedAt());
                BigDecimal lineTotal = line.getPrice() != null && Double.isFinite(line.getPrice()) && line.getQuantity() != null
                        ? BigDecimal.valueOf(line.getPrice()).multiply(BigDecimal.valueOf(line.getQuantity())) : null;
                return new ItemView(line.getId(), product == null ? null : product.getId(),
                        product == null ? "Sản phẩm không còn khả dụng" : product.getName(), product == null ? null : product.getImage(),
                        line.getQuantity(), line.getPrice(), lineTotal, OrderStatusPolicy.DELIVERED_STATUSES.contains(status)
                                && line.getQuantity() != null && line.getQuantity() > 0 && review == null,
                        reviewView, orderCases.stream().filter(c -> c.orderDetailId() == null || c.orderDetailId().equals(line.getId())).map(c -> c.id()).toList(),
                        OrderStatusPolicy.DELIVERED_STATUSES.contains(status) && product != null && product.getStock() != null
                                && product.getStock() > 0 && product.getPrice() != null && Double.isFinite(product.getPrice())
                                && product.getPrice() > 0);
            }).toList();
            List<CategoryView> groups = new ArrayList<>(); groups.add(category(CustomerOrderCategory.ALL));
            if (OrderStatusPolicy.PENDING_STATUSES.contains(status)) groups.add(category(CustomerOrderCategory.PENDING));
            if (OrderStatusPolicy.SHIPPING_STATUSES.contains(status)) groups.add(category(CustomerOrderCategory.SHIPPING));
            if (items.stream().anyMatch(item -> item.needsReview())) groups.add(category(CustomerOrderCategory.REVIEW));
            if (OrderStatusPolicy.DELIVERED_STATUSES.contains(status) && items.stream().noneMatch(item -> item.needsReview()))
                groups.add(category(CustomerOrderCategory.COMPLETED));
            if (!orderCases.isEmpty()) groups.add(category(CustomerOrderCategory.RETURNS));
            if (OrderStatusPolicy.CANCELLED.equals(status)) groups.add(category(CustomerOrderCategory.CANCELLED));
            return new OrderView(order.getId(), order.getOrderDate() == null ? null : order.getOrderDate().toInstant(), order.getStatus(),
                    order.getPaymentMethod(), order.getTotalAmount(), order.getVoucherCode(), order.getVoucherDiscount(), order.getSubtotalBeforeVoucher(),
                    order.getCustomerName(), order.getCustomerPhone(), order.getAddress(), List.copyOf(groups), items, orderCases);
        }).toList();
    }
    public static CategoryView category(CustomerOrderCategory group) { return new CategoryView(group.name(), group.getLabel()); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng hoặc sản phẩm."); }
}
