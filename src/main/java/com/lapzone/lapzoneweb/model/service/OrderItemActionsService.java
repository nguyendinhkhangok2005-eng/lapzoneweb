package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.OrderDetail;
import com.lapzone.lapzoneweb.model.entity.OrderItemReview;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.OrderDetailRepository;
import com.lapzone.lapzoneweb.model.repository.OrderItemReviewRepository;
import com.lapzone.lapzoneweb.model.repository.OrderRepository;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class OrderItemActionsService {
    private final OrderAuthorization authorization;
    private final OrderRepository orders;
    private final OrderDetailRepository details;
    private final OrderItemReviewRepository reviews;
    private final ProductRepository products;
    private final CartService cart;

    public OrderItemActionsService(OrderAuthorization authorization, OrderRepository orders, OrderDetailRepository details,
                                   OrderItemReviewRepository reviews, ProductRepository products, CartService cart) {
        this.authorization = authorization;
        this.orders = orders;
        this.details = details;
        this.reviews = reviews;
        this.products = products;
        this.cart = cart;
    }

    public record ReviewItem(Long orderId, Long itemId, String productName) {}

    @Transactional(readOnly = true)
    public ReviewItem eligibleReview(Long orderId, Long itemId) {
        User user = authorization.requireUser();
        Order order = orders.findByIdAndUserId(orderId, user.getId()).orElseThrow(this::missing);
        OrderDetail line = details.findByIdAndOrderId(itemId, orderId).orElseThrow(this::missing);
        checkReviewEligibility(order, line);
        return new ReviewItem(orderId, itemId, line.getProduct() == null ? "Sản phẩm đã mua" : line.getProduct().getName());
    }

    @Transactional
    public void submitReview(Long orderId, Long itemId, Integer rating, String content) {
        if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("Vui lòng chọn từ 1 đến 5 sao.");
        String written = content == null ? "" : content.trim();
        if (written.isEmpty() || written.length() > 4000 || written.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException("Nội dung đánh giá phải có từ 1 đến 4.000 ký tự hợp lệ.");
        User user = authorization.requireUser();
        Order order = orders.lockOwnedOrder(orderId, user.getId()).orElseThrow(this::missing);
        OrderDetail line = details.findByIdAndOrderId(itemId, orderId).orElseThrow(this::missing);
        checkReviewEligibility(order, line);
        OrderItemReview review = new OrderItemReview();
        review.setOrderDetail(line);
        review.setRating(rating);
        review.setContent(written);
        review.setCreatedAt(LocalDateTime.now());
        reviews.saveAndFlush(review);
    }

    @Transactional
    public void repurchase(Long orderId, Long itemId) {
        User user = authorization.requireUser();
        Order order = orders.findByIdAndUserId(orderId, user.getId()).orElseThrow(this::missing);
        OrderDetail line = details.findByIdAndOrderId(itemId, orderId).orElseThrow(this::missing);
        if (!OrderStatusPolicy.DELIVERED_STATUSES.contains(order.getStatus()) || line.getQuantity() == null || line.getQuantity() < 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm chưa đủ điều kiện mua lại.");
        Product original = line.getProduct();
        Product current = original == null ? null : products.findById(original.getId()).orElse(null);
        if (current == null || current.getStock() == null || current.getStock() < 1 || current.getPrice() == null
                || !Double.isFinite(current.getPrice()) || current.getPrice() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm hiện không còn bán hoặc đã hết hàng.");
        // CartService reads current product and stock again. Checkout remains responsible for final pricing and stock.
        if (!cart.addToCart(user.getId(), current.getId(), 1))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thêm sản phẩm vào giỏ. Vui lòng kiểm tra tồn kho.");
    }

    private void checkReviewEligibility(Order order, OrderDetail line) {
        if (!OrderStatusPolicy.DELIVERED_STATUSES.contains(order.getStatus()) || line.getQuantity() == null || line.getQuantity() < 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm chưa được giao hoặc không đủ điều kiện đánh giá.");
        if (reviews.existsByOrderDetailId(line.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm trong đơn này đã được đánh giá.");
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong đơn hàng của bạn.");
    }
}
