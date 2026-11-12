package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.CustomerOrdersPageController;
import com.lapzone.lapzoneweb.controller.OrderViewFormat;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.CustomerOrderCategory;
import com.lapzone.lapzoneweb.model.service.CustomerOrderService;
import com.lapzone.lapzoneweb.security.CustomAuthenticationSuccessHandler;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import com.lapzone.lapzoneweb.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerOrdersPageController.class)
@Import({SecurityConfig.class, OrderViewFormat.class})
class CustomerOrdersPageTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerOrderService orders;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    MockHttpSession customer() {
        User user = new User(); user.setId(10L); user.setRole("USER"); user.setEmail("khach@example.test");
        CustomUserDetails principal = new CustomUserDetails(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())));
        return session;
    }

    CustomerOrderService.OrderView order(long id, String image) {
        var line = new CustomerOrderService.ItemView(id + 1, 8L, "Laptop thực tế", image, 2,
                125000.0, BigDecimal.valueOf(250000), false, null, List.of());
        return new CustomerOrderService.OrderView(id, Instant.parse("2026-11-12T08:30:00Z"), "CHỜ XÁC NHẬN", "COD", 250000.0,
                null, null, null, "Người nhận", "0900000000", "Hà Nội",
                List.of(CustomerOrderService.category(CustomerOrderCategory.ALL)), List.of(line), List.of());
    }

    @Test void requiresAuthenticationForListAndDetail() throws Exception {
        mvc.perform(get("/store/orders")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/store/orders/42")).andExpect(status().is3xxRedirection());
        MockHttpSession stale = new MockHttpSession();
        User sessionOnly = new User(); sessionOnly.setId(11L); stale.setAttribute("currentUser", sessionOnly);
        when(carts.getCartItems(11L)).thenReturn(List.of());
        mvc.perform(get("/store/orders").session(stale)).andExpect(status().is3xxRedirection());
        verifyNoInteractions(orders);
    }

    @Test void rendersFiveTabsInExactOrderAndShowsRealStoredImagePriceAndNewestOrderFirst() throws Exception {
        var page = new CustomerOrderService.OrderPage(List.of(order(42, "assets/laptop-a.jpg"), order(21, "laptop-b.jpg")),
                0, 20, 2, 1, CustomerOrderService.category(CustomerOrderCategory.ALL));
        when(orders.list("ALL", 0, 20)).thenReturn(page);
        String html = mvc.perform(get("/store/orders").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đơn hàng #42")))
                .andExpect(content().string(containsString("Laptop thực tế")))
                .andExpect(content().string(containsString("/assets/laptop-a.jpg")))
                .andExpect(content().string(containsString("/assets/laptop-b.jpg")))
                .andExpect(content().string(containsString("125.000 ₫")))
                .andExpect(content().string(containsString("Số lượng: 2")))
                .andExpect(content().string(containsString("CHỜ XÁC NHẬN")))
                .andExpect(content().string(containsString("12/11/2026 15:30")))
                .andExpect(content().string(containsString("/store/orders/42?category=ALL")))
                .andReturn().getResponse().getContentAsString();
        String tabs = html.substring(html.indexOf("class=\"orders-tabs\""), html.indexOf("</nav>", html.indexOf("class=\"orders-tabs\"")));
        String[] labels = {"Tất cả", "Chờ xác nhận", "Chờ vận chuyển", "Cần đánh giá", "Trả hàng", "Đã hủy", "Hoàn thành"};
        int previous = -1;
        for (String label : labels) {
            int position = tabs.indexOf(">" + label + "</span>");
            assertTrue(position > previous, "Thứ tự tab không đúng: " + label);
            previous = position;
        }
        assertTrue(html.indexOf("Đơn hàng #42") < html.indexOf("Đơn hàng #21"));
        verify(orders).list("ALL", 0, 20);
    }

    @Test void everyCategoryHasARelevantEmptyStateAndPaginationKeepsSelection() throws Exception {
        String[] messages = {"Bạn chưa có đơn hàng nào", "Không có đơn chờ xác nhận", "Không có đơn chờ vận chuyển",
                "Không có sản phẩm cần đánh giá", "Chưa có hồ sơ trả hàng hoặc hoàn tiền",
                "Không có đơn hàng đã hủy", "Chưa có đơn hoàn thành không còn cần đánh giá"};
        CustomerOrderCategory[] groups = CustomerOrderCategory.values();
        for (int i = 0; i < groups.length; i++) {
            var group = groups[i];
            when(orders.list(group.name(), 0, 20)).thenReturn(new CustomerOrderService.OrderPage(List.of(), 0, 20,
                    0, 0, CustomerOrderService.category(group)));
            mvc.perform(get("/store/orders").session(customer()).param("category", group.name()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString(messages[i])));
        }
        when(orders.list("SHIPPING", 1, 20)).thenReturn(new CustomerOrderService.OrderPage(List.of(order(42, null)),
                1, 20, 55, 3, CustomerOrderService.category(CustomerOrderCategory.SHIPPING)));
        mvc.perform(get("/store/orders").session(customer()).param("category", "SHIPPING").param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("category=SHIPPING&amp;page=0")))
                .andExpect(content().string(containsString("category=SHIPPING&amp;page=2")))
                .andExpect(content().string(containsString("Chưa có ảnh sản phẩm")));
    }

    @Test void ownedDetailShowsActualItemsPaymentHistoryAndNoUnimplementedActions() throws Exception {
        var item = new CustomerOrderService.ItemView(43L, 8L, "Laptop thực tế", "assets/laptop-a.jpg", 2,
                125000.0, BigDecimal.valueOf(250000), true, null, List.of(4L));
        var caseItem = new CustomerOrderService.ReturnView(4L, 43L, "RETURN", "REQUESTED", "Máy lỗi", LocalDateTime.of(2026, 11, 12, 15, 0));
        var original = order(42, "assets/laptop-a.jpg");
        var purchase = new CustomerOrderService.OrderView(42L, original.orderDate(), "HOÀN THÀNH", "COD", 225000.0,
                "GIAM10", BigDecimal.valueOf(25000), BigDecimal.valueOf(250000), original.customerName(), original.customerPhone(),
                original.address(), List.of(CustomerOrderService.category(CustomerOrderCategory.REVIEW)), List.of(item), List.of(caseItem));
        when(orders.detail(42L)).thenReturn(new CustomerOrderService.DetailView(purchase,
                List.of(new CustomerOrderService.HistoryView("ĐANG GIAO HÀNG", "HOÀN THÀNH", LocalDateTime.of(2026, 11, 12, 15, 0)))));
        mvc.perform(get("/store/orders/42").session(customer()).param("category", "REVIEW"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đơn hàng #42")))
                .andExpect(content().string(containsString("225.000 ₫")))
                .andExpect(content().string(containsString("GIAM10")))
                .andExpect(content().string(containsString("Chưa đánh giá")))
                .andExpect(content().string(containsString("Máy lỗi")))
                .andExpect(content().string(containsString("Chờ xử lý")))
                .andExpect(content().string(containsString("/store/orders?category=REVIEW")))
                .andExpect(content().string(not(containsString("Yêu cầu hoàn tiền"))))
                .andExpect(content().string(containsString("/store/orders/42/items/43/review")))
                .andExpect(content().string(not(containsString("Gửi đánh giá</button>"))));
        verify(orders).detail(42L);
    }

    @Test void foreignOrderIsNotRenderedAndReturnsVietnamese404() throws Exception {
        when(orders.detail(999L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/store/orders/999").session(customer())).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Không tìm thấy đơn hàng hoặc bạn không có quyền xem đơn này.")))
                .andExpect(content().string(not(containsString("Laptop thực tế"))));
    }

    @Test void deliveredItemWithoutExistingCaseShowsTheRealRequestLink() throws Exception {
        var original = order(42, "assets/laptop-a.jpg");
        var delivered = new CustomerOrderService.OrderView(original.id(), original.orderDate(), "HOÀN THÀNH", original.paymentMethod(),
                original.totalAmount(), null, null, null, original.customerName(), original.customerPhone(), original.address(),
                original.categories(), original.items(), List.of());
        when(orders.detail(42L)).thenReturn(new CustomerOrderService.DetailView(delivered, List.of()));
        mvc.perform(get("/store/orders/42").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Yêu cầu hoàn tiền")))
                .andExpect(content().string(containsString("/store/orders/42/items/43/return-request")));
    }

    @Test void pendingPageShowsDistinctCodAndUnpaidVnpayBadgesWithActualOrderAndPaymentData() throws Exception {
        var cod = order(42, "assets/laptop-a.jpg");
        var online = new CustomerOrderService.OrderView(41L, cod.orderDate(), "CHỜ THANH TOÁN", "VNPAY",
                cod.totalAmount(), null, null, null, cod.customerName(), cod.customerPhone(), cod.address(),
                List.of(CustomerOrderService.category(CustomerOrderCategory.PENDING)), cod.items(), List.of());
        when(orders.list("PENDING", 0, 20)).thenReturn(new CustomerOrderService.OrderPage(List.of(cod, online),
                0, 20, 2, 1, CustomerOrderService.category(CustomerOrderCategory.PENDING)));
        mvc.perform(get("/store/orders").param("category", "PENDING").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"Giao nhận: Chờ xác nhận đơn hàng\">Chờ xác nhận đơn hàng</span>")))
                .andExpect(content().string(containsString("aria-label=\"Thanh toán: Chờ thanh toán\">Chờ thanh toán</span>")))
                .andExpect(content().string(containsString("Phương thức thanh toán:")))
                .andExpect(content().string(containsString("Thanh toán khi nhận hàng")))
                .andExpect(content().string(containsString("VNPay")))
                .andExpect(content().string(containsString("Đơn hàng #41")))
                .andExpect(content().string(containsString("Laptop thực tế")))
                .andExpect(content().string(containsString("Số lượng: 2")))
                .andExpect(content().string(containsString("250.000 ₫")));
        verify(orders).list("PENDING", 0, 20);
    }

    @Test void paidVnpayHasSeparatePaymentAndPendingFulfillmentBadgesOnListAndDetail() throws Exception {
        var original = order(42, "assets/laptop-a.jpg");
        var paid = new CustomerOrderService.OrderView(original.id(), original.orderDate(), "ĐÃ THANH TOÁN", "VNPAY",
                original.totalAmount(), null, null, null, original.customerName(), original.customerPhone(), original.address(),
                List.of(CustomerOrderService.category(CustomerOrderCategory.PENDING)), original.items(), List.of());
        when(orders.list("PENDING", 0, 20)).thenReturn(new CustomerOrderService.OrderPage(List.of(paid), 0, 20, 1, 1,
                CustomerOrderService.category(CustomerOrderCategory.PENDING)));
        when(orders.detail(42L)).thenReturn(new CustomerOrderService.DetailView(paid, List.of()));
        for (String path : List.of("/store/orders?category=PENDING", "/store/orders/42"))
            mvc.perform(get(path).session(customer())).andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-kind=\"payment\"")))
                    .andExpect(content().string(containsString("data-kind=\"fulfillment\"")))
                    .andExpect(content().string(containsString("aria-label=\"Thanh toán: Đã thanh toán\">Đã thanh toán</span>")))
                    .andExpect(content().string(containsString("aria-label=\"Giao nhận: Chờ xác nhận đơn hàng\">Chờ xác nhận đơn hàng</span>")))
                    .andExpect(content().string(not(containsString("Yêu cầu hoàn tiền"))));
    }

    @Test void tabBadgesUseServerOrderCountsAndExplainOverlappingGroups() throws Exception {
        var counts = java.util.Map.of("ALL", 5L, "PENDING", 1L, "SHIPPING", 1L, "REVIEW", 1L,
                "RETURNS", 1L, "CANCELLED", 2L, "COMPLETED", 0L);
        when(orders.list("ALL", 0, 20)).thenReturn(new CustomerOrderService.OrderPage(List.of(order(42, null)),
                0, 20, 5, 1, CustomerOrderService.category(CustomerOrderCategory.ALL), counts));
        mvc.perform(get("/store/orders").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"Tất cả: 5 đơn hàng\"")))
                .andExpect(content().string(containsString("aria-label=\"Đã hủy: 2 đơn hàng\"")))
                .andExpect(content().string(containsString("không phải số sản phẩm")))
                .andExpect(content().string(containsString("không cộng các nhóm")));
    }

    @Test void deliveredItemShowsReviewAndRepurchaseWhileReviewedItemHidesReview() throws Exception {
        var original = order(42, "assets/laptop-a.jpg");
        var pendingReview = new CustomerOrderService.ItemView(43L, 8L, "Laptop thực tế", "assets/laptop-a.jpg", 2,
                125000.0, BigDecimal.valueOf(250000), true, null, List.of(4L), true);
        var delivered = new CustomerOrderService.OrderView(original.id(), original.orderDate(), "HOÀN THÀNH", original.paymentMethod(),
                original.totalAmount(), null, null, null, original.customerName(), original.customerPhone(), original.address(),
                original.categories(), List.of(pendingReview), List.of());
        when(orders.detail(42L)).thenReturn(new CustomerOrderService.DetailView(delivered, List.of()));
        mvc.perform(get("/store/orders/42").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(containsString("/store/orders/42/items/43/review")))
                .andExpect(content().string(containsString("/store/orders/42/items/43/repurchase")))
                .andExpect(content().string(containsString("Mua lại")));
        var completed = new CustomerOrderService.ItemView(43L, 8L, "Laptop thực tế", "assets/laptop-a.jpg", 2,
                125000.0, BigDecimal.valueOf(250000), false,
                new CustomerOrderService.ReviewView(9L, 5, "<script>alert(1)</script>", LocalDateTime.now()), List.of(), true);
        var reviewed = new CustomerOrderService.OrderView(original.id(), original.orderDate(), "HOÀN THÀNH", original.paymentMethod(),
                original.totalAmount(), null, null, null, original.customerName(), original.customerPhone(), original.address(),
                original.categories(), List.of(completed), List.of());
        when(orders.detail(42L)).thenReturn(new CustomerOrderService.DetailView(reviewed, List.of()));
        mvc.perform(get("/store/orders/42").session(customer())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/store/orders/42/items/43/review"))))
                .andExpect(content().string(containsString("Đã đánh giá: 5/5 sao")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
