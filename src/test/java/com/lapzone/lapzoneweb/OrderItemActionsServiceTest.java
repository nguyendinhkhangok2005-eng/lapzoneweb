package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({OrderItemActionsService.class, OrderAuthorization.class, CustomerOrderService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderItemActionsServiceTest {
    @Autowired OrderItemActionsService actions;
    @Autowired CustomerOrderService customerOrders;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderDetailRepository details;
    @Autowired OrderItemReviewRepository reviews;
    @MockitoBean CartService cart;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User owner, other;
    Product product;
    Order order;
    OrderDetail line;

    @BeforeEach void setup() {
        owner = new User(); owner.setEmail(UUID.randomUUID() + "@example.test"); owner.setRole("USER"); owner = users.saveAndFlush(owner);
        other = new User(); other.setEmail(UUID.randomUUID() + "@example.test"); other.setRole("USER"); other = users.saveAndFlush(other);
        Category category = new Category(); category.setName("Đánh giá " + UUID.randomUUID()); category = categories.saveAndFlush(category);
        product = new Product(); product.setName("Máy đã mua"); product.setCategory(category); product.setPrice(200000.0); product.setStock(5);
        product = products.saveAndFlush(product);
        order = new Order(); order.setUser(owner); order.setStatus(OrderStatusPolicy.DELIVERED); order.setOrderDate(new Date()); order.setTotalAmount(100000.0);
        order = orders.saveAndFlush(order);
        line = new OrderDetail(); line.setOrder(order); line.setProduct(product); line.setQuantity(2); line.setPrice(50000.0);
        line = details.saveAndFlush(line);
        login(owner);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login(User user) {
        var principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @Test void reviewIsPersistedOnceAndRemovesCompletedLineFromReviewCategory() {
        assertEquals(1, customerOrders.list("REVIEW", 0, 20).totalElements());
        actions.submitReview(order.getId(), line.getId(), 5, "  Sản phẩm tốt.  ");
        var stored = reviews.findByOrderDetailOrderIdIn(java.util.List.of(order.getId())).getFirst();
        assertEquals(5, stored.getRating()); assertEquals("Sản phẩm tốt.", stored.getContent());
        assertEquals(line.getId(), stored.getOrderDetail().getId());
        assertEquals(0, customerOrders.list("REVIEW", 0, 20).totalElements());
        assertFalse(customerOrders.detail(order.getId()).order().items().getFirst().needsReview());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> actions.submitReview(order.getId(), line.getId(), 4, "Gửi lần nữa")).getStatusCode().value());
        assertEquals(1, reviews.findByOrderDetailOrderIdIn(java.util.List.of(order.getId())).size());
    }

    @Test void reviewRequiresDeliveryValidRatingWrittenContentAndOwnedLine() {
        assertThrows(IllegalArgumentException.class, () -> actions.submitReview(order.getId(), line.getId(), 0, "Tốt"));
        assertThrows(IllegalArgumentException.class, () -> actions.submitReview(order.getId(), line.getId(), 6, "Tốt"));
        assertThrows(IllegalArgumentException.class, () -> actions.submitReview(order.getId(), line.getId(), 3, " "));
        assertThrows(IllegalArgumentException.class, () -> actions.submitReview(order.getId(), line.getId(), 3, "x".repeat(4001)));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> actions.submitReview(order.getId(), line.getId() + 10000, 3, "Tốt")).getStatusCode().value());
        order.setStatus(OrderStatusPolicy.PACKING); orders.saveAndFlush(order);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> actions.submitReview(order.getId(), line.getId(), 3, "Tốt")).getStatusCode().value());
        assertTrue(reviews.findByOrderDetailOrderIdIn(java.util.List.of(order.getId())).isEmpty());
    }

    @Test void foreignUserAndGuestCannotReviewOrRepurchase() {
        login(other);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> actions.submitReview(order.getId(), line.getId(), 4, "Tốt")).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> actions.repurchase(order.getId(), line.getId())).getStatusCode().value());
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> actions.eligibleReview(order.getId(), line.getId()));
        verifyNoInteractions(cart);
    }

    @Test void repurchaseUsesCurrentCatalogAndCartWithoutChangingOriginalOrder() {
        when(cart.addToCart(owner.getId(), product.getId(), 1)).thenReturn(true);
        actions.repurchase(order.getId(), line.getId());
        verify(cart).addToCart(owner.getId(), product.getId(), 1);
        assertEquals(50000.0, details.findById(line.getId()).orElseThrow().getPrice());
        assertEquals(100000.0, orders.findById(order.getId()).orElseThrow().getTotalAmount());
        product.setStock(0); products.saveAndFlush(product);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> actions.repurchase(order.getId(), line.getId())).getStatusCode().value());
        verifyNoMoreInteractions(cart);
    }

    @Test void repurchaseRejectsUnavailableProductAndCartFailure() {
        product.setPrice(null); products.saveAndFlush(product);
        assertThrows(ResponseStatusException.class, () -> actions.repurchase(order.getId(), line.getId()));
        product.setPrice(250000.0); products.saveAndFlush(product);
        when(cart.addToCart(owner.getId(), product.getId(), 1)).thenReturn(false);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> actions.repurchase(order.getId(), line.getId())).getStatusCode().value());
        order.setStatus(OrderStatusPolicy.PENDING); orders.saveAndFlush(order);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> actions.repurchase(order.getId(), line.getId())).getStatusCode().value());
    }
}
