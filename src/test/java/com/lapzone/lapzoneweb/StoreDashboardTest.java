package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.config.StoreDirectoryProperties;
import com.lapzone.lapzoneweb.controller.StoreDirectoryController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StoreDirectoryController.class)
@Import({SecurityConfig.class, StoreDirectoryProperties.class})
class StoreDashboardTest {
    @Autowired MockMvc mvc;
    @Autowired StoreDirectoryProperties stores;
    @MockitoBean VoucherCatalogService vouchers;
    @MockitoBean SupportService support;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    @BeforeEach void setup() {
        stores.setBranches(List.of());
        when(vouchers.availableFor(any())).thenReturn(List.of());
        when(vouchers.availableFor(isNull())).thenReturn(List.of());
    }

    @Test void guestDashboardRendersNavigationEmptyStatesThemeAndChatbot() throws Exception {
        mvc.perform(get("/store")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cửa hàng")))
                .andExpect(content().string(containsString("/store/orders")))
                .andExpect(content().string(containsString("/tracuudonhang")))
                .andExpect(content().string(containsString("/support")))
                .andExpect(content().string(containsString("Thông tin cửa hàng chưa được cập nhật")))
                .andExpect(content().string(containsString("Hiện chưa có voucher phù hợp")))
                .andExpect(content().string(containsString("customer-theme.js")))
                .andExpect(content().string(containsString("/api/ai/chat")));
        verifyNoInteractions(support);
    }

    @Test void configuredBranchesRenderExactEncodedMapsAndEscapeTheirText() throws Exception {
        var first = new StoreDirectoryProperties.Branch(); first.setName("Chi nhánh kiểm thử một");
        first.setAddress("Địa chỉ kiểm thử & khu A"); first.setPhone("0900000000"); first.setOpeningHours("08:00–20:00");
        var second = new StoreDirectoryProperties.Branch(); second.setName("Chi nhánh kiểm thử hai");
        second.setAddress("Địa chỉ kiểm thử khu B");
        stores.setBranches(List.of(first, second));
        assertEquals("https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(first.getAddress(), StandardCharsets.UTF_8), first.getMapUrl());
        mvc.perform(get("/store")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chi nhánh kiểm thử một")))
                .andExpect(content().string(containsString("Chi nhánh kiểm thử hai")))
                .andExpect(content().string(containsString("08:00–20:00")))
                .andExpect(content().string(containsString("tel:0900000000")))
                .andExpect(content().string(containsString(first.getMapUrl().replace("&", "&amp;"))))
                .andExpect(content().string(containsString("noopener noreferrer")));
    }

    @Test void missingAddressHasNoMapAndEmptyBranchesAreIgnored() throws Exception {
        var branch = new StoreDirectoryProperties.Branch(); branch.setName("Chi nhánh chưa có địa chỉ");
        var empty = new StoreDirectoryProperties.Branch();
        stores.setBranches(List.of(branch, empty));
        assertNull(branch.getMapUrl()); assertFalse(empty.hasInformation());
        mvc.perform(get("/store")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có địa chỉ để mở bản đồ")))
                .andExpect(content().string(not(containsString("google.com/maps/search"))));
    }

    @Test void authenticatedDashboardShowsActualVoucherConditionsAndUnreadCount() throws Exception {
        User user = new User(); user.setId(10L); user.setRole("USER"); user.setEmail("customer@example.test");
        var principal = new CustomUserDetails(user); var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())));
        when(support.unreadCount(false)).thenReturn(3L);
        when(vouchers.availableFor(user)).thenReturn(List.of(new VoucherCatalogService.Offer("KIEMTHU5", 5,
                LocalDateTime.of(2026, 12, 31, 20, 0), BigDecimal.valueOf(100000), 7, 1,
                List.of("Sản phẩm kiểm thử"), List.of(), true)));
        mvc.perform(get("/store").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("3 tin chưa đọc")))
                .andExpect(content().string(containsString("KIEMTHU5")))
                .andExpect(content().string(containsString("5% giảm giá")))
                .andExpect(content().string(containsString("Sản phẩm kiểm thử")))
                .andExpect(content().string(containsString("100,000 đ")))
                .andExpect(content().string(not(containsString("Lưu voucher"))));
    }
}
