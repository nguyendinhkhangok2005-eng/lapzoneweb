package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({OrderItemActionsService.class, OrderAuthorization.class, CartService.class, OrderService.class, VoucherService.class})
class RepurchaseIntegrationTest {
    @Autowired OrderItemActionsService actions;
    @Autowired CartService cart;
    @Autowired OrderService checkout;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderDetailRepository details;
    @Autowired OrderReturnCaseRepository returns;
    @Autowired EntityManager entityManager;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User customer;
    Product product;
    Order original;
    OrderDetail line;

    @BeforeEach void setup() {
        customer = new User(); customer.setRole("USER"); customer.setEmail(UUID.randomUUID() + "@example.test"); customer = users.saveAndFlush(customer);
        Category category = new Category(); category.setName("Danh mục kiểm thử"); category = categories.saveAndFlush(category);
        product = new Product(); product.setCategory(category); product.setName("Sản phẩm kiểm thử"); product.setStock(3); product.setPrice(250000.0);
        product = products.saveAndFlush(product);
        original = new Order(); original.setUser(customer); original.setStatus(OrderStatusPolicy.DELIVERED);
        original.setOrderDate(new Date()); original.setPaymentMethod("COD"); original.setTotalAmount(100000.0); original = orders.saveAndFlush(original);
        line = new OrderDetail(); line.setOrder(original); line.setProduct(product); line.setQuantity(1); line.setPrice(100000.0); line = details.saveAndFlush(line);
        var principal = new CustomUserDetails(customer);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void returnedProductCanBeRepurchasedAtCurrentPriceThroughExistingCartAndCheckout() {
        OrderReturnCase returned = new OrderReturnCase(); returned.setOrder(original); returned.setOrderDetail(line);
        returned.setType(OrderReturnCase.Type.RETURN); returned.setStatus(OrderReturnCase.Status.COMPLETED);
        returned.setQuantity(1); returned.setCreatedAt(LocalDateTime.now()); returned = returns.saveAndFlush(returned);
        double currentPrice = PromotionService.getDiscountedPrice(product);
        long originalOrderCount = orders.count();
        actions.repurchase(original.getId(), line.getId());
        entityManager.flush(); entityManager.clear();
        var items = cart.getCartItems(customer.getId());
        assertEquals(1, items.size()); assertEquals(1, items.getFirst().getQuantity());
        assertEquals(product.getId(), items.getFirst().getProduct().getId());
        assertEquals(currentPrice, cart.getCartTotal(customer.getId()));
        assertEquals(originalOrderCount, orders.count());
        assertEquals(3, products.findById(product.getId()).orElseThrow().getStock());
        assertEquals(OrderReturnCase.Status.COMPLETED, returns.findById(returned.getId()).orElseThrow().getStatus());
        Order newOrder = checkout.placeOrder(customer, "Khách kiểm thử", "0900000000", "Địa chỉ kiểm thử",
                List.of(items.getFirst().getId()), "COD");
        entityManager.flush(); entityManager.clear();
        assertEquals(currentPrice, newOrder.getTotalAmount());
        assertEquals(2, products.findById(product.getId()).orElseThrow().getStock());
        assertTrue(cart.getCartItems(customer.getId()).isEmpty());
        assertEquals(100000.0, orders.findById(original.getId()).orElseThrow().getTotalAmount());
        assertEquals(100000.0, details.findById(line.getId()).orElseThrow().getPrice());
    }

    @Test void repurchaseCannotIncreaseCartBeyondCurrentStock() {
        assertTrue(cart.addToCart(customer.getId(), product.getId(), 3));
        assertThrows(ResponseStatusException.class, () -> actions.repurchase(original.getId(), line.getId()));
        assertEquals(3, cart.getCartItems(customer.getId()).getFirst().getQuantity());
        assertEquals(1, orders.count());
    }
}
