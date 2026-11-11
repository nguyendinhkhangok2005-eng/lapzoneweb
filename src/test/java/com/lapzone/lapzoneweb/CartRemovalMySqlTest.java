package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

// Opt in against the configured MySQL database with -Dlapzone.test.mysql=true.
// Only isolated fixture rows are inserted/deleted; schema updates are disabled.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CartService.class, OrderService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "lapzone.test.mysql", matches = "true")
class CartRemovalMySqlTest {
    @Autowired CartService service;
    @Autowired OrderService orderService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CacheManager cacheManager;
    @MockitoSpyBean CartItemRepository repository;
    String marker = "cart-removal-" + UUID.randomUUID();
    Long owner;
    Long stranger;
    Long category;

    @AfterEach
    void cleanup() {
        if (owner != null) {
            jdbc.update("DELETE FROM order_details WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", owner);
            jdbc.update("DELETE FROM orders WHERE user_id = ?", owner);
        }
        if (owner != null) jdbc.update("DELETE FROM cart_items WHERE user_id = ?", owner);
        if (stranger != null) jdbc.update("DELETE FROM cart_items WHERE user_id = ?", stranger);
        if (category != null) jdbc.update("DELETE FROM products WHERE category_id = ?", category);
        if (category != null) jdbc.update("DELETE FROM categories WHERE id = ?", category);
        if (owner != null) jdbc.update("DELETE FROM users WHERE id = ?", owner);
        if (stranger != null) jdbc.update("DELETE FROM users WHERE id = ?", stranger);
    }

    @Test
    void deletionCommitsAndFreshReadsPreserveOtherItemsAndOwnership() {
        owner = createUser("owner");
        stranger = createUser("stranger");
        jdbc.update("INSERT INTO categories(name) VALUES (?)", marker);
        category = jdbc.queryForObject("SELECT id FROM categories WHERE name = ?", Long.class, marker);
        Long firstProduct = createProduct("first");
        Long secondProduct = createProduct("second");
        Long selected = createItem(owner, firstProduct, 1);
        Long remaining = createItem(owner, secondProduct, 2);
        Long foreign = createItem(stranger, firstProduct, 3);

        // The service proxy commits before these independent JDBC reads execute.
        CartService.RemovalResult result = service.removeFromCart(owner, selected);
        assertEquals(2, result.cartItemCount());
        assertEquals(20_000_000.0, result.cartTotal());
        assertEquals(0, rowCount(selected));
        assertEquals(2, jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id = ?", Integer.class, remaining));
        assertEquals(1, rowCount(foreign));
        assertEquals(remaining, service.getCartItems(owner).getFirst().getId());
        assertEquals(1, service.getSelectedCartItems(java.util.List.of(selected, remaining), owner).size());

        assertNull(service.removeFromCart(owner, foreign));
        assertEquals(1, rowCount(foreign));
        assertNull(service.removeFromCart(owner, selected));
        assertEquals(new CartService.RemovalResult(0, 0), service.removeFromCart(owner, remaining));
        assertTrue(service.getCartItems(owner).isEmpty());
        assertEquals(0, rowCount(remaining));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = ?", Integer.class, category));
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, firstProduct));
    }

    @Test
    void failureAfterDeleteRollsBackTheDatabaseChange() {
        owner = createUser("rollback");
        jdbc.update("INSERT INTO categories(name) VALUES (?)", marker);
        category = jdbc.queryForObject("SELECT id FROM categories WHERE name = ?", Long.class, marker);
        Long selected = createItem(owner, createProduct("rollback"), 1);
        doThrow(new IllegalStateException("summary query failed")).when(repository).findByUserIdWithProduct(owner);

        assertThrows(IllegalStateException.class, () -> service.removeFromCart(owner, selected));
        assertEquals(1, rowCount(selected));
    }

    @Test
    void remainingProductCanBeUpdatedAndCheckedOutAfterRemoval() {
        owner = createUser("checkout");
        jdbc.update("INSERT INTO categories(name) VALUES (?)", marker);
        category = jdbc.queryForObject("SELECT id FROM categories WHERE name = ?", Long.class, marker);
        Long first = createProduct("remove");
        Long second = createProduct("checkout");
        assertTrue(service.addToCart(owner, first, 1));
        assertTrue(service.addToCart(owner, second, 2));
        Long selected = jdbc.queryForObject("SELECT id FROM cart_items WHERE user_id = ? AND product_id = ?",
                Long.class, owner, first);
        Long remaining = jdbc.queryForObject("SELECT id FROM cart_items WHERE user_id = ? AND product_id = ?",
                Long.class, owner, second);
        assertNotNull(service.removeFromCart(owner, selected));
        service.updateQuantity(owner, remaining, 3);
        User user = new User();
        user.setId(owner);
        Order order = orderService.placeOrder(user, "Khách kiểm thử", "0900000000", "Địa chỉ kiểm thử",
                java.util.List.of(selected, remaining), "COD");

        assertEquals(30_000_000.0, order.getTotalAmount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_details WHERE order_id = ?", Integer.class, order.getId()));
        assertEquals(second, jdbc.queryForObject("SELECT product_id FROM order_details WHERE order_id = ?", Long.class, order.getId()));
        assertEquals(7, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, second));
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, first));
        assertTrue(service.getCartItems(owner).isEmpty());
    }

    private Long createUser(String suffix) {
        String email = marker + suffix + "@example.test";
        jdbc.update("INSERT INTO users(full_name,email,phone,password,role) VALUES (?,?,?,?,?)",
                marker, email, "0900000000", "unused-test-password", "USER");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long createProduct(String suffix) {
        jdbc.update("INSERT INTO products(name,price,stock,category_id) VALUES (?,?,?,?)",
                marker + suffix, 10_000_000.0, 10, category);
        return jdbc.queryForObject("SELECT id FROM products WHERE name = ?", Long.class, marker + suffix);
    }

    private Long createItem(Long user, Long product, int quantity) {
        jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES (?,?,?)", user, product, quantity);
        return jdbc.queryForObject("SELECT id FROM cart_items WHERE user_id = ? AND product_id = ?", Long.class, user, product);
    }

    private int rowCount(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id = ?", Integer.class, id);
    }
}
