package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CustomerOrderController.class, AdminOrderController.class})
@Import(SecurityConfig.class)
class OrderManagementSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerOrderService customerOrders;
    @MockitoBean OrderService orders;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;
    MockHttpSession session(String role) {
        var user = new User(); user.setId(10L); user.setRole(role); user.setEmail("customer@example.test");
        var principal = new CustomUserDetails(user); var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()))); return session;
    }
    @Test void anonymousAndSessionOnlyUsersCannotReadOrderApiAndCustomersCannotManage() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").exists());
        var fake = new MockHttpSession(); fake.setAttribute("currentUser", new User());
        mvc.perform(get("/api/orders/7").session(fake)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/orders").session(session("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/orders/7/data").session(session("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/update-status").session(session("USER")).param("orderId", "7").param("newStatus", "HOÀN THÀNH"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customerOrders, orders);
    }
    @Test void exposesActualCategoryAndItemDataAndDoesNotAcceptClientOwnerId() throws Exception {
        var category = CustomerOrderService.category(CustomerOrderCategory.PENDING);
        var result = new CustomerOrderService.OrderPage(List.of(), 0, 20, 0, 0, category);
        when(customerOrders.list("PENDING", 0, 20)).thenReturn(result);
        var session = session("USER");
        mvc.perform(get("/api/orders").session(session).param("category", "PENDING").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category.label").value("Chờ xác nhận"));
        verify(customerOrders).list("PENDING", 0, 20);
        mvc.perform(get("/api/orders/categories").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(7))
                .andExpect(jsonPath("$.categoriesMayOverlap").value(true))
                .andExpect(jsonPath("$.reviewSubmissionSupported").value(true));
        when(customerOrders.detail(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/orders/99").session(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng hoặc sản phẩm."));
    }
    @Test void adminSeesOnlyValidNextOptionsAndCsrfProtectsStatusMutation() throws Exception {
        Order order = new Order(); order.setId(7L); order.setStatus("CHỜ XÁC NHẬN"); order.setPaymentMethod("COD");
        order.setOrderDate(new Date()); order.setTotalAmount(90000.0); order.setCustomerName("Khách hàng");
        when(orders.searchOrdersPaged(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(order)));
        var admin = session("ADMIN");
        var result = mvc.perform(get("/admin/orders").session(admin)).andExpect(status().isOk())
                .andExpect(model().attribute("statusOptions", Map.of(7L, List.of("ĐANG ĐÓNG GÓI", "ĐÃ HỦY"))))
                .andExpect(content().string(containsString("Trạng thái tiếp theo"))).andReturn();
        mvc.perform(post("/admin/orders/update-status").session(admin).param("orderId", "7").param("newStatus", "ĐANG ĐÓNG GÓI"))
                .andExpect(status().isForbidden());
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post("/admin/orders/update-status").session(admin).param(token.getParameterName(), token.getToken())
                        .param("orderId", "7").param("newStatus", "ĐANG ĐÓNG GÓI"))
                .andExpect(redirectedUrl("/admin/orders"));
        verify(orders).updateOrderStatus(7L, "ĐANG ĐÓNG GÓI");
    }
}
