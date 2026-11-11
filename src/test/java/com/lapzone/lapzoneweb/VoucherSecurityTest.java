package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminVoucherController.class, CheckoutController.class})
@Import(SecurityConfig.class)
class VoucherSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean VoucherAdminService admin;
    @MockitoBean VoucherService vouchers;
    @MockitoBean VoucherCatalogService voucherCatalog;
    @MockitoBean OrderService orders;
    @MockitoBean CartService carts;
    @MockitoBean ProductService products;
    @MockitoBean CategoryRepository categories;
    @MockitoBean CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    User user(String role) {
        User user = new User(); user.setId(71L); user.setEmail("user@example.test"); user.setRole(role); return user;
    }
    MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        if (role != null) {
            CustomUserDetails details = new CustomUserDetails(user(role));
            session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities())));
        }
        return session;
    }
    CsrfToken csrf(MockHttpSession session) throws Exception {
        Product p = new Product(); p.setId(1L); p.setPrice(100.0); p.setStock(5);
        CartItem item = new CartItem(); item.setProduct(p); item.setQuantity(1);
        session.setAttribute("BUY_NOW_ITEM", item);
        when(products.getProductById(1L)).thenReturn(p);
        MvcResult result = mvc.perform(get("/checkout").param("mode", "buynow").session(session))
                .andExpect(status().isOk()).andReturn();
        return (CsrfToken) result.getRequest().getAttribute("_csrf");
    }

    @Test
    void checkoutRendersAvailableVoucherTermsAndSelection() throws Exception {
        var offer = new VoucherCatalogService.Offer("SAVE5", 5,
                java.time.LocalDateTime.of(2026, 12, 1, 9, 0), BigDecimal.valueOf(50000),
                3, 1, List.of("Máy kiểm thử"), List.of("Laptop"), false);
        when(voucherCatalog.availableFor(any())).thenReturn(List.of(offer));
        MockHttpSession session = session("USER");
        Product product = new Product(); product.setId(1L); product.setPrice(100000.0); product.setStock(2);
        CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(1);
        session.setAttribute("BUY_NOW_ITEM", item);
        when(products.getProductById(1L)).thenReturn(product);
        mvc.perform(get("/checkout").param("mode", "buynow").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dùng mã này")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Máy kiểm thử")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SAVE5")));
    }

    @Test
    void anonymousAndCustomerCannotManageVouchers() throws Exception {
        mvc.perform(get("/admin/vouchers")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/vouchers").session(session("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @Test
    void administratorRequiresCsrfForMutation() throws Exception {
        mvc.perform(post("/admin/vouchers/1/deactivate").session(session("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @Test
    void administratorCanCreateAndViewManagementPageWithCsrf() throws Exception {
        when(admin.list(anyInt())).thenReturn(Page.empty());
        MockHttpSession session = session("ADMIN");
        MvcResult result = mvc.perform(get("/admin/vouchers").session(session)).andExpect(status().isOk()).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post("/admin/vouchers/save").session(session).param(token.getParameterName(), token.getToken())
                .param("code", "SAVE5").param("discountPercent", "5").param("quantity", "10")
                .param("perUserLimit", "1").param("startsAt", "2026-11-01T09:00").param("endsAt", "2026-12-01T09:00")
                .param("minimumOrderValue", "0").param("active", "true"))
                .andExpect(redirectedUrl("/admin/vouchers"));
        verify(admin).save(isNull(), argThat(f -> f.getDiscountPercent() == 5 && f.getCode().equals("SAVE5")));
    }

    @Test
    void previewUsesAuthenticatedOwnerAndDoesNotAcceptClientTotalsOrUserId() throws Exception {
        MockHttpSession session = session("USER");
        CsrfToken token = csrf(session);
        when(vouchers.preview(eq("SAVE5"), any(User.class), anyList())).thenReturn(
            new VoucherService.Quote(BigDecimal.valueOf(100), BigDecimal.valueOf(5), BigDecimal.valueOf(95), Map.of()));
        mvc.perform(post("/checkout/voucher-preview").session(session).param(token.getParameterName(), token.getToken())
                .param("mode", "buynow").param("voucherCode", "SAVE5").param("userId", "999")
                .param("total", "1").param("discount", "100000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(95));
        verify(vouchers).preview(eq("SAVE5"), argThat(u -> u.getId().equals(71L)), anyList());
        verifyNoInteractions(orders);
    }

    @Test
    void guestCannotApplyVoucherUsingOnlySessionUserAndPreviewIsCsrfProtected() throws Exception {
        MockHttpSession session = session(null); session.setAttribute("currentUser", user("USER"));
        mvc.perform(post("/checkout/voucher-preview").session(session).param("voucherCode", "SAVE5"))
                .andExpect(status().isForbidden());
        CsrfToken token = csrf(session);
        mvc.perform(post("/checkout/voucher-preview").session(session).param(token.getParameterName(), token.getToken())
                .param("voucherCode", "SAVE5").param("mode", "buynow"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vouchers);
    }

    @Test
    void checkoutRejectsUnissuedTokenAndDoesNotCreateOrder() throws Exception {
        MockHttpSession session = session("USER"); session.setAttribute("currentUser", user("USER"));
        CsrfToken token = csrf(session);
        mvc.perform(post("/checkout/process").session(session).param(token.getParameterName(), token.getToken())
                .param("mode", "buynow").param("voucherCode", "SAVE5").param("checkoutKey", UUID.randomUUID().toString())
                .param("customerName", "Khách hàng").param("customerPhone", "0900000000")
                .param("address", "Địa chỉ").param("paymentMethod", "COD"))
                .andExpect(redirectedUrl("/checkout?mode=buynow"));
        verifyNoInteractions(orders);
    }
}
