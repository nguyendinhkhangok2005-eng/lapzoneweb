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
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({ReturnRequestService.class, OrderService.class, CustomerOrderService.class, OrderAuthorization.class, VoucherService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReturnRequestServiceTest {
    @Autowired ReturnRequestService service;
    @Autowired OrderService orderService;
    @Autowired CustomerOrderService customerOrders;
    @Autowired OrderRepository orders;
    @Autowired OrderDetailRepository details;
    @Autowired OrderStatusChangeRepository orderHistory;
    @Autowired OrderReturnCaseRepository requests;
    @MockitoSpyBean OrderReturnEventRepository events;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired VoucherRepository vouchers;
    @Autowired VoucherRedemptionRepository redemptions;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User owner, other, admin;
    Product product;
    Order order;
    OrderDetail line;

    @BeforeEach void setup() {
        owner = user("USER"); other = user("USER"); admin = user("ADMIN");
        Category category = new Category(); category.setName("Trả hàng " + UUID.randomUUID()); category = categories.saveAndFlush(category);
        product = new Product(); product.setName("Máy đã mua"); product.setCategory(category); product.setStock(20); product.setPrice(100000.0);
        product = products.saveAndFlush(product);
        order = orderService.placeOrderBuyNow(owner, "Người nhận", "090", "Địa chỉ", buyNow(), "COD");
        login(admin);
        orderService.updateOrderStatus(order.getId(), OrderStatusPolicy.PACKING);
        orderService.updateOrderStatus(order.getId(), OrderStatusPolicy.SHIPPING);
        orderService.updateOrderStatus(order.getId(), OrderStatusPolicy.DELIVERED);
        line = details.findByOrderId(order.getId()).getFirst(); login(owner);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(events); }
    User user(String role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role); return users.saveAndFlush(user);
    }
    void login(User user) {
        var principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }
    CartItem buyNow() { CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(2); return item; }
    ReturnRequestService.RequestView submit() { return service.submit(order.getId(), line.getId(), 1, "REFUND", "Sản phẩm gặp lỗi."); }

    @Test void submitPersistsQuantityReasonAndAuditWithoutChangingThePurchase() {
        var eligible = service.eligibleItem(order.getId(), line.getId()); assertEquals(2, eligible.maximumQuantity());
        var result = submit();
        assertEquals(1, result.quantity()); assertEquals("REFUND", result.type()); assertEquals("REQUESTED", result.status());
        assertEquals("Sản phẩm gặp lỗi.", requests.findById(result.id()).orElseThrow().getReason());
        assertEquals(1, service.history(result.id()).size());
        assertEquals(owner.getId(), events.findByRequestIdOrderByIdAsc(result.id()).getFirst().getActor().getId());
        assertEquals(1, customerOrders.list("RETURNS", 0, 20).totalElements());
        assertEquals(1, customerOrders.detail(order.getId()).order().returnCases().getFirst().quantity());
        assertPurchaseUnchanged();
    }

    @Test void validatesDeliveredStatusQuantitiesReasonAndType() {
        for (Integer quantity : Arrays.asList(null, 0, -1, 3))
            assertThrows(IllegalArgumentException.class, () -> service.submit(order.getId(), line.getId(), quantity, "REFUND", "Lý do hợp lệ"));
        assertThrows(IllegalArgumentException.class, () -> service.submit(order.getId(), line.getId(), 1, "HACK", "Lý do hợp lệ"));
        assertThrows(IllegalArgumentException.class, () -> service.submit(order.getId(), line.getId(), 1, "RETURN", " "));
        assertThrows(IllegalArgumentException.class, () -> service.submit(order.getId(), line.getId(), 1, "RETURN", "x".repeat(2001)));
        Order undelivered = orderService.placeOrderBuyNow(owner, "Người nhận", "090", "Địa chỉ", buyNow(), "COD");
        Long pendingLine = details.findByOrderId(undelivered.getId()).getFirst().getId();
        assertThrows(IllegalArgumentException.class, () -> service.submit(undelivered.getId(), pendingLine, 1, "REFUND", "Lý do hợp lệ"));
        assertTrue(requests.findByOrderIdIn(List.of(order.getId())).isEmpty());
    }

    @Test void rejectsForeignOwnersForeignLineIdsGuestsAndCustomerAdminImpersonation() {
        login(other);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> submit()).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.eligibleItem(order.getId(), line.getId()));
        login(owner); var request = submit();
        login(other); assertThrows(ResponseStatusException.class, () -> service.history(request.id()));
        assertThrows(AccessDeniedException.class, () -> service.decide(request.id(), "APPROVED", "", false));
        login(owner); assertThrows(ResponseStatusException.class, () -> service.submit(order.getId(), line.getId() + 1000000, 1, "REFUND", "Lý do hợp lệ"));
        SecurityContextHolder.clearContext(); assertThrows(AccessDeniedException.class, () -> service.eligibleItem(order.getId(), line.getId()));
        login(admin); admin.setRole("USER"); users.saveAndFlush(admin);
        assertThrows(AccessDeniedException.class, () -> service.decide(request.id(), "APPROVED", "", false));
    }

    @Test void allowsOnlyRequestedToApprovedOrRejectedThenApprovedToManualCompletion() {
        var request = submit(); login(admin);
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "COMPLETED", "Đã xử lý", true));
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "REJECTED", " ", false));
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "CANCELLED", "Không hợp lệ", false));
        service.decide(request.id(), "APPROVED", "Đã kiểm tra.", false);
        service.decide(request.id(), "APPROVED", "Không được ghi đè", false);
        assertEquals("Đã kiểm tra.", requests.findById(request.id()).orElseThrow().getAdminNote());
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "REJECTED", "Không được đảo trạng thái", false));
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "COMPLETED", "Đã đối soát", false));
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "COMPLETED", "", true));
        service.decide(request.id(), "COMPLETED", "Đã đối soát chứng từ xử lý ngoài hệ thống.", true);
        service.decide(request.id(), "COMPLETED", "Không ghi đè", true);
        assertEquals(3, events.findByRequestIdOrderByIdAsc(request.id()).size());
        var persisted = requests.findById(request.id()).orElseThrow();
        assertNotNull(persisted.getReviewedAt()); assertNotNull(persisted.getCompletedAt());
        assertEquals(admin.getId(), persisted.getCompletedBy().getId()); assertPurchaseUnchanged();
    }

    @Test void duplicateRequestsStayBlockedAfterRejectionAndDatabaseAlsoEnforcesUniqueness() {
        var request = submit();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> submit()).getStatusCode().value());
        login(admin); service.decide(request.id(), "REJECTED", "Không đủ điều kiện sau kiểm tra.", false);
        assertThrows(IllegalArgumentException.class, () -> service.decide(request.id(), "APPROVED", "", false));
        login(owner); assertThrows(ResponseStatusException.class, () -> submit());
        OrderReturnCase duplicate = new OrderReturnCase(); duplicate.setOrder(order); duplicate.setOrderDetail(line);
        duplicate.setQuantity(1); duplicate.setType(OrderReturnCase.Type.RETURN); duplicate.setCreatedAt(LocalDateTime.now());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> requests.saveAndFlush(duplicate));
        assertEquals(1, requests.findByOrderIdIn(List.of(order.getId())).size());
    }

    @Test void concurrentSubmissionsHaveOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<Boolean> submission = () -> {
            login(owner);
            try { start.await(); submit(); return true; }
            catch (ResponseStatusException e) { if (e.getStatusCode().value() != 409) throw e; return false; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = executor.submit(submission); var second = executor.submit(submission); start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, requests.findByOrderIdIn(List.of(order.getId())).size());
        } finally { start.countDown(); executor.shutdownNow(); }
        assertPurchaseUnchanged();
    }

    @Test void auditStorageFailuresRollBackCreationAndAdminDecisions() {
        doThrow(new IllegalStateException("storage failed")).when(events).save(any(OrderReturnEvent.class));
        assertThrows(IllegalStateException.class, () -> submit());
        assertTrue(requests.findByOrderIdIn(List.of(order.getId())).isEmpty()); reset(events);
        var request = submit(); login(admin);
        doThrow(new IllegalStateException("storage failed")).when(events).save(any(OrderReturnEvent.class));
        assertThrows(IllegalStateException.class, () -> service.decide(request.id(), "APPROVED", "Đã kiểm tra", false));
        var stored = requests.findById(request.id()).orElseThrow();
        assertEquals(OrderReturnCase.Status.REQUESTED, stored.getStatus()); assertNull(stored.getReviewedAt());
    }

    @Test void completingVoucherOrderRequestDoesNotRefundOrReleaseVoucherOrRestock() {
        Voucher voucher = new Voucher(); voucher.setCode(("RET" + UUID.randomUUID().toString().replace("-", "").substring(0, 18)).toUpperCase(Locale.ROOT));
        voucher.setDiscountPercent(5); voucher.setQuantity(10); voucher.setStartsAt(LocalDateTime.now(VoucherService.ZONE).minusDays(1));
        voucher.setEndsAt(LocalDateTime.now(VoucherService.ZONE).plusDays(1)); voucher = vouchers.saveAndFlush(voucher);
        login(owner);
        Order purchase = orderService.placeOrderBuyNow(owner, "Người nhận", "090", "Địa chỉ", buyNow(), "COD", voucher.getCode(), UUID.randomUUID().toString());
        login(admin); orderService.updateOrderStatus(purchase.getId(), OrderStatusPolicy.PACKING);
        orderService.updateOrderStatus(purchase.getId(), OrderStatusPolicy.SHIPPING); orderService.updateOrderStatus(purchase.getId(), OrderStatusPolicy.DELIVERED);
        login(owner); var request = service.submit(purchase.getId(), details.findByOrderId(purchase.getId()).getFirst().getId(), 1, "RETURN", "Cần hỗ trợ sản phẩm");
        login(admin); service.decide(request.id(), "APPROVED", "Đã kiểm tra", false);
        service.decide(request.id(), "COMPLETED", "Đã xử lý theo chứng từ riêng", true);
        assertEquals(purchase.getTotalAmount(), orders.findById(purchase.getId()).orElseThrow().getTotalAmount());
        assertEquals(1, vouchers.findById(voucher.getId()).orElseThrow().getRedeemedCount());
        assertEquals(VoucherRedemption.State.REDEEMED, redemptions.findByOrderId(purchase.getId()).orElseThrow().getState());
        assertEquals(16, products.findById(product.getId()).orElseThrow().getStock());
    }

    void assertPurchaseUnchanged() {
        var saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(order.getTotalAmount(), saved.getTotalAmount()); assertEquals("COD", saved.getPaymentMethod());
        assertEquals(OrderStatusPolicy.DELIVERED, saved.getStatus()); assertEquals(3, orderHistory.findByOrderIdOrderByIdAsc(order.getId()).size());
        assertEquals(18, products.findById(product.getId()).orElseThrow().getStock());
        assertEquals(line.getPrice(), details.findByOrderId(order.getId()).getFirst().getPrice());
    }
}
