package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static com.lapzone.lapzoneweb.model.service.OrderStatusPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({OrderService.class, CustomerOrderService.class, OrderAuthorization.class, VoucherService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderWorkflowTest {
    @Autowired OrderService service;
    @Autowired CustomerOrderService customerOrders;
    @Autowired OrderRepository orders;
    @Autowired OrderDetailRepository details;
    @Autowired CartItemRepository carts;
    @Autowired OrderStatusChangeRepository history;
    @Autowired OrderItemReviewRepository reviews;
    @Autowired OrderReturnCaseRepository returns;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User owner, other, admin;
    Product product;

    @BeforeEach void setup() {
        owner = user("USER"); other = user("USER"); admin = user("ADMIN");
        Category category = new Category(); category.setName("Kiểm thử " + UUID.randomUUID()); category = categories.saveAndFlush(category);
        product = new Product(); product.setName("Máy kiểm thử"); product.setCategory(category); product.setPrice(100000.0); product.setStock(30);
        product = products.saveAndFlush(product); login(owner);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    User user(String role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role); return users.saveAndFlush(user);
    }
    void login(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }
    Order place(User user, String payment) {
        CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(1);
        return service.placeOrderBuyNow(user, "Khách hàng", "0900000000", "Địa chỉ kiểm thử", item, payment);
    }
    void deliver(Long id) {
        login(admin); service.updateOrderStatus(id, PACKING); service.updateOrderStatus(id, SHIPPING); service.updateOrderStatus(id, DELIVERED);
    }
    int stock() { return products.findById(product.getId()).orElseThrow().getStock(); }

    @Test void confirmedShippingAndDeliveredKeepPricesStockAndActualHistory() {
        Order order = place(owner, "COD"); double payable = order.getTotalAmount();
        assertEquals(29, stock());
        login(admin);
        service.updateOrderStatus(order.getId(), PACKING);
        assertEquals(List.of(SHIPPING, CANCELLED), OrderStatusPolicy.allowed(orders.findById(order.getId()).orElseThrow()));
        service.updateOrderStatus(order.getId(), SHIPPING);
        service.updateOrderStatus(order.getId(), DELIVERED);
        service.updateOrderStatus(order.getId(), DELIVERED);
        assertEquals(29, stock());
        assertEquals(payable, orders.findById(order.getId()).orElseThrow().getTotalAmount());
        var changes = history.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(3, changes.size()); assertEquals(admin.getId(), changes.getFirst().getActor().getId());
        login(owner);
        var result = customerOrders.detail(order.getId());
        assertEquals(DELIVERED, result.order().status()); assertEquals(3, result.statusHistory().size());
        assertTrue(result.order().items().getFirst().needsReview());
        assertEquals(details.findByOrderId(order.getId()).getFirst().getPrice(), result.order().items().getFirst().unitPrice());
        assertEquals(payable, result.order().totalAmount());
    }

    @Test void forbidsSkippingReversingUnknownAndTerminalTransitionsAndInvoiceBypass() {
        Order order = place(owner, "COD"); login(admin);
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(order.getId(), DELIVERED));
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(order.getId(), "GIẢ MẠO"));
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(order.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> service.updateInvoiceDetails(order.getId(), "Tên mới", "090", "Địa chỉ mới", order.getTotalAmount(), DELIVERED));
        assertEquals("Khách hàng", orders.findById(order.getId()).orElseThrow().getCustomerName());
        deliver(order.getId());
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(order.getId(), CANCELLED));
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(order.getId(), PENDING));
        assertEquals(29, stock());
        assertEquals(3, history.findByOrderIdOrderByIdAsc(order.getId()).size());
    }

    @Test void paymentMustBeConfirmedBeforeShippingAndFailureAndCancellationRestockOnlyOnce() {
        Order unpaid = place(owner, "VNPAY"); login(admin);
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(unpaid.getId(), PACKING));
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(unpaid.getId(), PENDING));
        service.updateOrderStatus(unpaid.getId(), PAYMENT_FAILED); service.updateOrderStatus(unpaid.getId(), PAYMENT_FAILED);
        assertEquals(30, stock());
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(unpaid.getId(), PAID));
        Order cod = place(owner, "COD");
        service.updateOrderStatus(cod.getId(), CANCELLED); service.updateOrderStatus(cod.getId(), CANCELLED);
        assertEquals(30, stock());
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(cod.getId(), PACKING));
        Order online = place(owner, "VNPAY");
        service.updateOrderStatus(online.getId(), PAID); service.updateOrderStatus(online.getId(), PACKING);
        assertThrows(IllegalArgumentException.class, () -> service.updateOrderStatus(online.getId(), WAITING_PAYMENT));
        assertEquals(29, stock());
    }

    @Test void customersGuestsAndDowngradedAdminsCannotMutateOrders() {
        Order order = place(owner, "COD");
        assertThrows(AccessDeniedException.class, () -> service.updateOrderStatus(order.getId(), PACKING));
        assertThrows(AccessDeniedException.class, () -> service.updateInvoiceDetails(order.getId(), "Tên", "090", "Địa chỉ", 1.0, PACKING));
        assertThrows(AccessDeniedException.class, () -> service.deleteInvoiceAndDetails(order.getId()));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.updateOrderStatus(order.getId(), PACKING));
        login(admin); admin.setRole("USER"); users.saveAndFlush(admin);
        assertThrows(AccessDeniedException.class, () -> service.updateOrderStatus(order.getId(), PACKING));
        assertEquals(PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(29, stock());
    }

    @Test void customerQueriesExcludeForeignAndGuestOrdersAndForeignItems() {
        Order owned = place(owner, "COD"); Order foreign = place(other, "COD"); place(null, "COD");
        login(owner);
        var page = customerOrders.list("ALL", 0, 20);
        assertEquals(1, page.totalElements()); assertEquals(owned.getId(), page.content().getFirst().id());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> customerOrders.detail(foreign.getId())).getStatusCode().value());
        Long foreignItem = details.findByOrderId(foreign.getId()).getFirst().getId();
        assertThrows(ResponseStatusException.class, () -> customerOrders.item(owned.getId(), foreignItem));
        assertThrows(AccessDeniedException.class, () -> customerOrders.adminDetail(owned.getId()));
        login(admin); assertEquals(foreign.getId(), customerOrders.adminDetail(foreign.getId()).order().id());
    }

    @Test void pendingIncludesOwnedCodAndUnpaidVnpayWithoutChangingPaymentOrMatchingGuestIdentity() {
        Order cod = place(owner, "COD");
        Order unpaid = place(owner, "VNPAY");
        Order paid = place(owner, "VNPAY");
        Order foreign = place(other, "VNPAY");
        Order guest = place(null, "VNPAY"); // All fixtures share name/phone, but ownership must remain explicit.
        login(admin); service.updateOrderStatus(paid.getId(), PAID);
        assertEquals(guest.getId(), customerOrders.adminDetail(guest.getId()).order().id());
        assertEquals(foreign.getId(), customerOrders.adminDetail(foreign.getId()).order().id());
        login(owner);
        int stockBeforeRead = stock();
        var page = customerOrders.list("PENDING", 0, 20);
        assertEquals(Set.of(cod.getId(), unpaid.getId(), paid.getId()),
                new HashSet<>(page.content().stream().map(order -> order.id()).toList()));
        assertEquals(3, page.totalElements());
        assertEquals(3, customerOrders.list("PENDING", 0, 1).totalPages());
        var online = page.content().stream().filter(o -> o.id().equals(unpaid.getId())).findFirst().orElseThrow();
        assertEquals(WAITING_PAYMENT, online.status()); assertEquals("VNPAY", online.paymentMethod());
        assertEquals(unpaid.getTotalAmount(), online.totalAmount());
        assertEquals(1, online.items().getFirst().quantity()); assertEquals(product.getId(), online.items().getFirst().productId());
        assertTrue(online.categories().stream().anyMatch(c -> c.key().equals("PENDING")));
        assertEquals(WAITING_PAYMENT, customerOrders.detail(unpaid.getId()).order().status());
        assertEquals(3, customerOrders.list("ALL", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("SHIPPING", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("REVIEW", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("COMPLETED", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("CANCELLED", 0, 20).totalElements());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> customerOrders.detail(guest.getId())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> customerOrders.detail(foreign.getId())).getStatusCode().value());
        assertEquals(WAITING_PAYMENT, orders.findById(unpaid.getId()).orElseThrow().getStatus());
        assertTrue(history.findByOrderIdOrderByIdAsc(unpaid.getId()).isEmpty());
        assertEquals(stockBeforeRead, stock());
        assertFalse(OrderStatusPolicy.allowed(orders.findById(unpaid.getId()).orElseThrow()).contains(PACKING));
    }

    @Test void categoriesUseActualStatusesReviewsAndOrderOrItemReturnCases() {
        Order pending = place(owner, "COD"), paid = place(owner, "VNPAY"), packing = place(owner, "COD"), shipping = place(owner, "COD"), delivered = place(owner, "COD");
        login(admin); service.updateOrderStatus(paid.getId(), PAID); service.updateOrderStatus(packing.getId(), PACKING);
        service.updateOrderStatus(shipping.getId(), PACKING); service.updateOrderStatus(shipping.getId(), SHIPPING); deliver(delivered.getId());
        login(owner);
        assertEquals(5, customerOrders.list("ALL", 0, 2).totalElements());
        assertEquals(2, customerOrders.list("PENDING", 0, 20).totalElements());
        assertEquals(2, customerOrders.list("SHIPPING", 0, 20).totalElements());
        assertEquals(1, customerOrders.list("REVIEW", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("RETURNS", 0, 20).totalElements());
        OrderDetail purchased = details.findByOrderId(delivered.getId()).getFirst();
        var review = new OrderItemReview(); review.setOrderDetail(purchased); review.setRating(5); review.setContent("Tốt"); review.setCreatedAt(LocalDateTime.now()); reviews.saveAndFlush(review);
        assertEquals(0, customerOrders.list("REVIEW", 0, 20).totalElements());
        assertEquals(5, customerOrders.list("ALL", 0, 20).totalElements());
        assertEquals(1, customerOrders.list("COMPLETED", 0, 20).totalElements());
        assertEquals(0, customerOrders.list("CANCELLED", 0, 20).totalElements());
        var itemCase = new OrderReturnCase(); itemCase.setOrder(delivered); itemCase.setOrderDetail(purchased);
        itemCase.setType(OrderReturnCase.Type.RETURN); itemCase.setCreatedAt(LocalDateTime.now()); returns.saveAndFlush(itemCase);
        var wholeCase = new OrderReturnCase(); wholeCase.setOrder(pending); wholeCase.setType(OrderReturnCase.Type.REFUND); wholeCase.setCreatedAt(LocalDateTime.now()); returns.saveAndFlush(wholeCase);
        assertEquals(2, customerOrders.list("RETURNS", 0, 20).totalElements());
        var counts = customerOrders.categoryCounts();
        assertEquals(5L, counts.get("ALL")); assertEquals(2L, counts.get("RETURNS"));
        assertEquals(1L, counts.get("COMPLETED")); assertEquals(0L, counts.get("REVIEW"));
        assertEquals(1, customerOrders.list("COMPLETED", 0, 20).totalElements());
        var line = customerOrders.detail(delivered.getId()).order().items().getFirst();
        assertNotNull(line.review()); assertFalse(line.needsReview()); assertEquals(List.of(itemCase.getId()), line.returnCaseIds());
        assertEquals(List.of(wholeCase.getId()), customerOrders.detail(pending.getId()).order().items().getFirst().returnCaseIds());
    }

    @Test void cannotAttachReturnCaseToAnotherOrdersItemAndRejectsInvalidFilters() {
        Order first = place(owner, "COD"), second = place(owner, "COD");
        var request = new OrderReturnCase(); request.setOrder(first); request.setOrderDetail(details.findByOrderId(second.getId()).getFirst());
        request.setType(OrderReturnCase.Type.RETURN); request.setCreatedAt(LocalDateTime.now());
        assertThrows(RuntimeException.class, () -> returns.saveAndFlush(request));
        assertThrows(IllegalArgumentException.class, () -> customerOrders.list("KHÔNG CÓ", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> customerOrders.list("ALL", -1, 20));
        assertThrows(IllegalArgumentException.class, () -> customerOrders.list("ALL", 0, 1000));
    }

    @Test void partialReviewsAndReturnsRemainLinkedToTheCorrectPurchasedLine() {
        Product second = new Product(); second.setName("Máy thứ hai"); second.setCategory(product.getCategory());
        second.setPrice(200000.0); second.setStock(10); second = products.saveAndFlush(second);
        CartItem firstCart = new CartItem(); firstCart.setProduct(product); firstCart.setQuantity(1); firstCart.setUserId(owner.getId()); firstCart = carts.saveAndFlush(firstCart);
        CartItem secondCart = new CartItem(); secondCart.setProduct(second); secondCart.setQuantity(1); secondCart.setUserId(owner.getId()); secondCart = carts.saveAndFlush(secondCart);
        Order order = service.placeOrder(owner, "Khách hàng", "090", "Địa chỉ", List.of(firstCart.getId(), secondCart.getId()), "COD");
        deliver(order.getId()); login(owner);
        var lines = details.findByOrderId(order.getId()).stream().sorted(Comparator.comparing(line -> line.getId())).toList();
        OrderItemReview review = new OrderItemReview(); review.setOrderDetail(lines.getFirst()); review.setRating(4); review.setCreatedAt(LocalDateTime.now()); reviews.saveAndFlush(review);
        OrderReturnCase request = new OrderReturnCase(); request.setOrder(order); request.setOrderDetail(lines.getFirst());
        request.setType(OrderReturnCase.Type.RETURN); request.setCreatedAt(LocalDateTime.now()); returns.saveAndFlush(request);
        var view = customerOrders.detail(order.getId()).order();
        assertFalse(view.items().getFirst().needsReview()); assertTrue(view.items().getLast().needsReview());
        assertEquals(List.of(request.getId()), view.items().getFirst().returnCaseIds());
        assertTrue(view.items().getLast().returnCaseIds().isEmpty());
        assertEquals(1, customerOrders.list("REVIEW", 0, 20).totalElements());
        assertEquals(1, customerOrders.list("RETURNS", 0, 20).totalElements());
        assertEquals(order.getTotalAmount(), view.totalAmount());
    }
}
