package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.*;
import com.lapzone.lapzoneweb.model.entity.User;
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
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ReturnRequestPageController.class, ReturnRequestApiController.class})
@Import({SecurityConfig.class, OrderViewFormat.class})
class ReturnRequestControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReturnRequestService service;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    MockHttpSession session(String role) {
        User user = new User(); user.setId(10L); user.setRole(role); user.setEmail("customer@example.test");
        var principal = new CustomUserDetails(user); var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()))); return session;
    }
    CsrfToken csrf(MockHttpSession session) throws Exception {
        when(service.eligibleItem(7L, 8L)).thenReturn(new ReturnRequestService.EligibleItem(7L, 8L, "Máy đã mua", "assets/may.jpg", 2, 100000.0));
        var response = mvc.perform(get("/store/orders/7/items/8/return-request").session(session)).andExpect(status().isOk()).andReturn();
        return (CsrfToken) response.getRequest().getAttribute("_csrf");
    }
    ReturnRequestService.RequestView request(String status) {
        return new ReturnRequestService.RequestView(1L, 7L, 8L, "Khách hàng", "Máy đã mua", 1, "REFUND", status,
                "<script>alert('reason')</script>", LocalDateTime.of(2026, 11, 12, 10, 0), null, null, null, null);
    }

    @Test void authenticationAdminRoleAndCsrfProtectAllMutations() throws Exception {
        mvc.perform(get("/store/orders/7/items/8/return-request")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/returns").session(session("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/store/orders/7/items/8/return-request").session(session("USER"))
                .param("quantity", "1").param("reason", "Lý do hợp lệ")).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/7/items/8/return-requests").session(session("USER")).contentType("application/json")
                .content("{\"quantity\":1,\"type\":\"REFUND\",\"reason\":\"Lý do hợp lệ\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/returns/1/decision").session(session("ADMIN")).param("status", "APPROVED"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void customerFormSubmitsOnlyPathItemQuantityTypeAndReason() throws Exception {
        var session = session("USER"); CsrfToken token = csrf(session);
        when(service.submit(7L, 8L, 1, "REFUND", "Máy gặp lỗi")).thenReturn(request("REQUESTED"));
        mvc.perform(post("/store/orders/7/items/8/return-request").session(session).param(token.getParameterName(), token.getToken())
                .param("quantity", "1").param("type", "REFUND").param("reason", "Máy gặp lỗi")
                .param("status", "APPROVED").param("userId", "999"))
                .andExpect(redirectedUrl("/store/orders/7?category=RETURNS"))
                .andExpect(flash().attribute("success", containsString("Đã gửi yêu cầu")));
        verify(service).submit(7L, 8L, 1, "REFUND", "Máy gặp lỗi");
        mvc.perform(post("/admin/returns/1/decision").session(session).param(token.getParameterName(), token.getToken()).param("status", "APPROVED"))
                .andExpect(status().isForbidden());
        verify(service, never()).decide(anyLong(), anyString(), anyString(), anyBoolean());
    }

    @Test void jsonEndpointAcceptsCsrfAndReturnsPersistedRequestData() throws Exception {
        var session = session("USER"); CsrfToken token = csrf(session);
        when(service.submit(7L, 8L, 1, "REFUND", "Máy gặp lỗi")).thenReturn(request("REQUESTED"));
        mvc.perform(post("/api/orders/7/items/8/return-requests").session(session).header(token.getHeaderName(), token.getToken())
                .contentType("application/json").content("{\"quantity\":1,\"type\":\"REFUND\",\"reason\":\"Máy gặp lỗi\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantity").value(1)).andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test void adminQueueEscapesReasonAndSupportsReviewAndExplicitManualCompletion() throws Exception {
        when(service.adminList(0, "")).thenReturn(new PageImpl<>(List.of(request("REQUESTED"), request("APPROVED"))));
        var admin = session("ADMIN");
        var result = mvc.perform(get("/admin/returns").session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chờ xử lý")))
                .andExpect(content().string(containsString("Đã chấp nhận")))
                .andExpect(content().string(containsString("Hoàn tất hồ sơ")))
                .andExpect(content().string(containsString("manualProcessingConfirmed")))
                .andExpect(content().string(not(containsString("<script>alert('reason')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;"))).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post("/admin/returns/1/decision").session(admin).param(token.getParameterName(), token.getToken())
                .param("status", "APPROVED").param("note", "Đã kiểm tra"))
                .andExpect(redirectedUrl("/admin/returns"));
        verify(service).decide(1L, "APPROVED", "Đã kiểm tra", false);
        mvc.perform(post("/admin/returns/1/decision").session(admin).param(token.getParameterName(), token.getToken())
                .param("status", "COMPLETED").param("note", "Đã xử lý theo chứng từ").param("manualProcessingConfirmed", "true"))
                .andExpect(redirectedUrl("/admin/returns"));
        verify(service).decide(1L, "COMPLETED", "Đã xử lý theo chứng từ", true);
    }

    @Test void duplicateAndOwnershipErrorsRemainClearAndDoNotExposeForeignData() throws Exception {
        var customer = session("USER"); CsrfToken token = csrf(customer);
        when(service.submit(7L, 8L, 1, "REFUND", "Máy gặp lỗi")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu."));
        mvc.perform(post("/store/orders/7/items/8/return-request").session(customer).param(token.getParameterName(), token.getToken())
                .param("quantity", "1").param("type", "REFUND").param("reason", "Máy gặp lỗi"))
                .andExpect(redirectedUrl("/store/orders/7?category=RETURNS"))
                .andExpect(flash().attribute("error", "Sản phẩm đã có yêu cầu."));
        when(service.eligibleItem(99L, 8L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoặc sản phẩm."));
        mvc.perform(get("/store/orders/99/items/8/return-request").session(customer)).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Máy đã mua"))));
    }

    @Test void validationKeepsTheSelectedRequestTypeQuantityAndReason() throws Exception {
        var customer = session("USER"); CsrfToken token = csrf(customer);
        when(service.submit(7L, 8L, 3, "RETURN", "Máy gặp lỗi")).thenThrow(new IllegalArgumentException("Số lượng vượt quá số đã mua."));
        mvc.perform(post("/store/orders/7/items/8/return-request").session(customer).param(token.getParameterName(), token.getToken())
                .param("quantity", "3").param("type", "RETURN").param("reason", "Máy gặp lỗi"))
                .andExpect(redirectedUrl("/store/orders/7/items/8/return-request"))
                .andExpect(flash().attribute("draftType", "RETURN"))
                .andExpect(flash().attribute("draftQuantity", 3))
                .andExpect(flash().attribute("draftReason", "Máy gặp lỗi"));
    }
}
