package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({VoucherService.class, VoucherCatalogService.class, OrderService.class, OrderAuthorization.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoucherTransactionTest {
    @Autowired VoucherService service;
    @Autowired VoucherCatalogService catalog;
    @Autowired OrderService orders;
    @Autowired VoucherRepository vouchers;
    @Autowired VoucherRedemptionRepository redemptions;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired CartItemRepository carts;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean OrderDetailRepository details;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.cache.CacheManager cacheManager;
    User user;
    Product product;
    Voucher voucher;

    @BeforeEach
    void setup() {
        User administrator = new User(); administrator.setEmail(UUID.randomUUID() + "@admin.test"); administrator.setRole("ADMIN");
        administrator = users.saveAndFlush(administrator);
        var principal = new com.lapzone.lapzoneweb.security.CustomUserDetails(administrator);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
        user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole("USER");
        user = users.saveAndFlush(user);
        Category category = new Category(); category.setName("Kiểm thử " + UUID.randomUUID());
        category = categories.saveAndFlush(category);
        product = new Product(); product.setName("Máy kiểm thử"); product.setPrice(100000.0);
        product.setStock(20); product.setCategory(category);
        product = products.saveAndFlush(product);
        voucher = new Voucher(); voucher.setCode("V" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        voucher.setDiscountPercent(10); voucher.setQuantity(5); voucher.setPerUserLimit(2);
        voucher.setStartsAt(LocalDateTime.now(VoucherService.ZONE).minusDays(1));
        voucher.setEndsAt(LocalDateTime.now(VoucherService.ZONE).plusDays(1));
        voucher = vouchers.saveAndFlush(voucher);
    }

    CartItem item() { CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(1); return item; }

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test
    void catalogShowsOnlyUsableVouchersAndRealConditions() {
        edit(v -> {
            v.setMinimumOrderValue(BigDecimal.valueOf(50000));
            v.getProductIds().add(product.getId());
            v.getCategoryIds().add(product.getCategory().getId());
        });
        var offer = catalog.availableFor(user).stream().filter(v -> v.code().equals(voucher.getCode())).findFirst().orElseThrow();
        assertEquals(voucher.getCode(), offer.code());
        assertEquals(5, offer.availableQuantity());
        assertEquals(List.of(product.getName()), offer.productNames());
        assertEquals(List.of(product.getCategory().getName()), offer.categoryNames());
        assertEquals(0, offer.minimumOrderValue().compareTo(BigDecimal.valueOf(50000)));

        edit(v -> v.getUserIds().add(user.getId()));
        assertTrue(catalog.availableFor(null).stream().noneMatch(v -> v.code().equals(voucher.getCode())));
        assertTrue(catalog.availableFor(user).stream().anyMatch(v -> v.code().equals(voucher.getCode())));
        place(UUID.randomUUID().toString(), "COD");
        assertEquals(4, catalog.availableFor(user).stream().filter(v -> v.code().equals(voucher.getCode()))
                .findFirst().orElseThrow().availableQuantity());
        edit(v -> v.setQuantity(1));
        assertTrue(catalog.availableFor(user).stream().noneMatch(v -> v.code().equals(voucher.getCode())));
        assertEquals("Mã giảm giá đã hết lượt sử dụng.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.preview(voucher.getCode(), user, List.of(item()))).getMessage());
    }
    Order place(String key, String method) {
        return orders.placeOrderBuyNow(user, "Khách hàng", "0900000000", "Địa chỉ kiểm thử", item(), method, voucher.getCode(), key);
    }
    Voucher current() { return vouchers.findById(voucher.getId()).orElseThrow(); }
    void edit(java.util.function.Consumer<Voucher> edit) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Voucher v = vouchers.lockById(voucher.getId()).orElseThrow(); edit.accept(v); vouchers.saveAndFlush(v);
        });
    }

    @Test
    void previewsDoNotConsumeAndOrderPreservesPromotionAndTotals() {
        var first = service.preview(" " + voucher.getCode().toLowerCase(Locale.ROOT) + " ", user, List.of(item()));
        service.preview(voucher.getCode(), user, List.of(item()));
        assertEquals(5, current().getAvailableQuantity());
        assertTrue(redemptions.findByVoucherIdOrderByIdDesc(voucher.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).isEmpty());
        BigDecimal sale = BigDecimal.valueOf(PromotionService.getDiscountedPrice(product));
        assertEquals(0, sale.compareTo(first.subtotal()));
        Order order = place(UUID.randomUUID().toString(), "VNPAY");
        assertEquals(first.total().doubleValue(), order.getTotalAmount());
        assertEquals(0, first.discount().compareTo(order.getVoucherDiscount()));
        assertEquals(sale.doubleValue(), details.findByOrderId(order.getId()).getFirst().getPrice());
        assertEquals(1, current().getReservedCount());
        assertEquals(0, current().getRedeemedCount());
        assertEquals(19, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void rejectsExpiredFutureInactiveGuestAndUnauthorizedAccounts() {
        assertThrows(IllegalArgumentException.class, () -> service.preview(voucher.getCode(), null, List.of(item())));
        edit(v -> v.setEndsAt(LocalDateTime.now(VoucherService.ZONE).minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        edit(v -> { v.setStartsAt(LocalDateTime.now(VoucherService.ZONE).plusDays(1)); v.setEndsAt(v.getStartsAt().plusDays(1)); });
        assertThrows(IllegalArgumentException.class, () -> service.preview(voucher.getCode(), user, List.of(item())));
        edit(v -> { v.setStartsAt(LocalDateTime.now(VoucherService.ZONE).minusDays(1)); v.setActive(false); });
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        edit(v -> { v.setActive(true); v.getUserIds().add(user.getId() + 100000); });
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        assertEquals(20, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void categoryAndProductEligibilityAndMinimumUseCurrentServerPrices() {
        edit(v -> v.getProductIds().add(product.getId() + 10000));
        assertThrows(IllegalArgumentException.class, () -> service.preview(voucher.getCode(), user, List.of(item())));
        edit(v -> v.getCategoryIds().add(product.getCategory().getId()));
        assertNotNull(service.preview(voucher.getCode(), user, List.of(item())));
        edit(v -> v.setMinimumOrderValue(BigDecimal.valueOf(200000)));
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        edit(v -> { v.setMinimumOrderValue(BigDecimal.ZERO); v.getCategoryIds().clear(); v.getProductIds().add(product.getId()); });
        Product forged = new Product(); forged.setId(product.getId()); forged.setPrice(1.0);
        CartItem request = item(); request.setProduct(forged);
        assertTrue(service.preview(voucher.getCode(), user, List.of(request)).subtotal().compareTo(BigDecimal.ONE) > 0);
    }

    @Test
    void enforcesPersonalLimitAndDuplicateKeyWithoutSecondStockChange() {
        edit(v -> v.setPerUserLimit(1));
        String key = UUID.randomUUID().toString();
        place(key, "COD");
        assertThrows(IllegalArgumentException.class, () -> place(key, "COD"));
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        assertEquals(19, products.findById(product.getId()).orElseThrow().getStock());
        assertEquals(1, current().getReservedCount());
    }

    @Test
    void cancellationReleasesOnceAndCannotReopen() {
        edit(v -> v.setPerUserLimit(1));
        Order order = place(UUID.randomUUID().toString(), "COD");
        orders.updateOrderStatus(order.getId(), "ĐÃ HỦY");
        orders.updateOrderStatus(order.getId(), "ĐÃ HỦY");
        assertEquals(5, current().getAvailableQuantity());
        assertEquals(VoucherRedemption.State.RELEASED, redemptions.findByOrderId(order.getId()).orElseThrow().getState());
        assertEquals(20, products.findById(product.getId()).orElseThrow().getStock());
        assertThrows(IllegalArgumentException.class, () -> orders.updateOrderStatus(order.getId(), "HOÀN THÀNH"));
        assertNotNull(place(UUID.randomUUID().toString(), "COD"));
    }

    @Test
    void paymentConfirmationRedeemsOnceAndPaidCancellationKeepsUsage() {
        edit(v -> v.setPerUserLimit(1));
        Order order = place(UUID.randomUUID().toString(), "VNPAY");
        orders.updateOrderStatus(order.getId(), "ĐÃ THANH TOÁN");
        orders.updateOrderStatus(order.getId(), "ĐÃ THANH TOÁN");
        assertEquals(0, current().getReservedCount());
        assertEquals(1, current().getRedeemedCount());
        orders.updateOrderStatus(order.getId(), "ĐÃ HỦY");
        assertEquals(1, current().getRedeemedCount());
        assertEquals(4, current().getAvailableQuantity());
        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        assertThrows(IllegalArgumentException.class, () -> orders.deleteInvoiceAndDetails(order.getId()));
        assertThrows(IllegalArgumentException.class, () -> orders.updateInvoiceDetails(order.getId(), "Tên", "090", "Địa chỉ", 1.0, "ĐÃ HỦY"));
    }

    @Test
    void failedPaymentReleasesReservationAndStockOnce() {
        Order order = place(UUID.randomUUID().toString(), "VNPAY");
        orders.updateOrderStatus(order.getId(), "THANH TOÁN THẤT BẠI");
        orders.updateOrderStatus(order.getId(), "THANH TOÁN THẤT BẠI");
        assertEquals(5, current().getAvailableQuantity());
        assertEquals(20, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void failureAfterReservationRollsBackOrderStockAndCapacity() {
        doThrow(new IllegalStateException("storage failed")).when(details).save(any(OrderDetail.class));
        try {
            assertThrows(IllegalStateException.class, () -> place(UUID.randomUUID().toString(), "COD"));
        } finally { reset(details); }
        assertEquals(5, current().getAvailableQuantity());
        assertEquals(20, products.findById(product.getId()).orElseThrow().getStock());
        assertTrue(redemptions.findByVoucherIdOrderByIdDesc(voucher.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).isEmpty());
        assertTrue(orderRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void existingCartCheckoutUsesOnlyOwnedItemsAndAppliesVoucher() {
        CartItem owned = item(); owned.setUserId(user.getId()); owned = carts.saveAndFlush(owned);
        CartItem foreign = item(); foreign.setUserId(user.getId() + 100000); foreign = carts.saveAndFlush(foreign);
        final Long foreignId = foreign.getId();
        assertThrows(IllegalArgumentException.class, () -> orders.placeOrder(user, "Khách hàng", "090", "Địa chỉ",
                List.of(foreignId), "COD", voucher.getCode(), UUID.randomUUID().toString()));
        var quote = service.preview(voucher.getCode(), user, List.of(item()));
        Order order = orders.placeOrder(user, "Khách hàng", "090", "Địa chỉ", List.of(owned.getId(), foreignId),
                "COD", voucher.getCode(), UUID.randomUUID().toString());
        assertEquals(quote.total().doubleValue(), order.getTotalAmount());
        assertEquals(1, details.findByOrderId(order.getId()).size());
        assertFalse(carts.existsById(owned.getId()));
        assertTrue(carts.existsById(foreignId));
    }

    @Test
    void simultaneousRedemptionsOfFinalVoucherHaveExactlyOneWinner() throws Exception {
        edit(v -> v.setQuantity(1));
        assertOneConcurrentWinner();
        assertEquals(0, current().getAvailableQuantity());
    }

    @Test
    void simultaneousRequestsRespectPerAccountLimitEvenWithAvailableCapacity() throws Exception {
        edit(v -> v.setPerUserLimit(1));
        assertOneConcurrentWinner();
        assertEquals(4, current().getAvailableQuantity());
    }

    @Test
    void onlyEligibleLinesAreDiscountedAndMoneyIsRoundedToDong() {
        product.setPrice(100005.0); products.saveAndFlush(product);
        Product other = new Product(); other.setName("Sản phẩm không áp dụng"); other.setPrice(200000.0);
        other.setStock(10); other.setCategory(product.getCategory()); other = products.saveAndFlush(other);
        CartItem otherItem = new CartItem(); otherItem.setProduct(other); otherItem.setQuantity(2);
        edit(v -> v.getProductIds().add(product.getId()));
        var quote = service.preview(voucher.getCode(), user, List.of(item(), otherItem));
        BigDecimal eligible = BigDecimal.valueOf(PromotionService.getDiscountedPrice(product));
        BigDecimal expected = eligible.multiply(BigDecimal.valueOf(10)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(quote.discount()));
        assertEquals(0, eligible.add(BigDecimal.valueOf(PromotionService.getDiscountedPrice(other)).multiply(BigDecimal.TWO)).subtract(expected).compareTo(quote.total()));
    }

    @Test
    void databaseUniqueCheckoutKeyRejectsReuseWithAnotherVoucherAndRollsBackStock() {
        String key = UUID.randomUUID().toString();
        place(key, "COD");
        Voucher second = new Voucher(); second.setCode("SECOND" + voucher.getId()); second.setDiscountPercent(5);
        second.setQuantity(3); second.setStartsAt(voucher.getStartsAt()); second.setEndsAt(voucher.getEndsAt());
        second = vouchers.saveAndFlush(second);
        // Separate voucher means a separate row lock; the global unique key still protects the order.
        voucher = second;
        assertThrows(IllegalArgumentException.class, () -> place(key, "COD"));
        assertEquals(19, products.findById(product.getId()).orElseThrow().getStock());
        assertEquals(0, current().getReservedCount());
    }

    private void assertOneConcurrentWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> task = () -> {
                ready.countDown(); start.await();
                try { place(UUID.randomUUID().toString(), "COD"); return true; }
                catch (IllegalArgumentException e) { return false; }
            };
            Future<Boolean> a = executor.submit(task); Future<Boolean> b = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int winners = (a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            assertEquals(1, current().getReservedCount());
            assertEquals(19, products.findById(product.getId()).orElseThrow().getStock());
        } finally { start.countDown(); executor.shutdownNow(); }
    }
}
