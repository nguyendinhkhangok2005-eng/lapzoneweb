package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AiFeedbackController.class, AdminAiFeedbackController.class})
@Import(SecurityConfig.class)
class AiFeedbackControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiFeedbackService feedback;
    @MockitoBean CartService cartService;
    @MockitoBean CategoryRepository categories;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    MockHttpSession login(String role) {
        User user = new User(); user.setId(1L); user.setRole(role);
        var principal = new CustomUserDetails(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession(); session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context); return session;
    }
    com.fasterxml.jackson.databind.JsonNode token(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/ai/feedback/csrf").session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    @Test void feedbackRequiresCsrfAndUsesTheServerSideGuestScope() throws Exception {
        MockHttpSession guest = new MockHttpSession();
        String path = "/api/ai/conversations/own/messages/42/feedback";
        mvc.perform(post(path).session(guest).contentType("application/json").content("{\"rating\":\"POSITIVE\"}"))
            .andExpect(status().isForbidden()); verifyNoInteractions(feedback);
        var csrf = token(guest);
        when(feedback.submit(eq("own"), eq(42L), any(), any())).thenReturn(new AiFeedbackService.Vote(42L, "POSITIVE", null));
        mvc.perform(post(path).session(guest).header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType("application/json").content("{\"rating\":\"POSITIVE\",\"ownerId\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.messageId").value(42));
        verify(feedback).submit(eq("own"), eq(42L), argThat(a -> a.ownerId() == null && a.guestKey() != null), any());
    }

    @Test void adminReportsAndReviewAreRoleProtectedAndReviewRequiresCsrf() throws Exception {
        mvc.perform(get("/admin/ai/feedback")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/ai/feedback").session(login("USER"))).andExpect(status().isForbidden());
        MockHttpSession admin = login("ADMIN");
        mvc.perform(post("/admin/ai/feedback/1/review").session(admin)).andExpect(status().isForbidden());
        verifyNoInteractions(feedback);
        var csrf = token(admin);
        mvc.perform(post("/admin/ai/feedback/1/review").session(admin)
            .header(csrf.get("headerName").asText(), csrf.get("token").asText())).andExpect(status().is3xxRedirection());
        verify(feedback).review(1L);
    }

    @Test void rendersVietnameseFiltersAndOnlyTheRestrictedReportFields() throws Exception {
        var date = LocalDate.of(2026, 11, 12);
        var report = new AiFeedbackService.Report(1L, 42L, "abcd1234", "Tiêu cực", "Thông tin sai.", "12/11/2026 10:00", "Chưa xem xét", false);
        when(feedback.reports(eq("NEGATIVE"), eq("INCORRECT"), eq(date), eq(date), eq(0)))
            .thenReturn(new AiFeedbackService.ReportPage("NEGATIVE", "INCORRECT", date, date, new PageImpl<>(List.of(report))));
        mvc.perform(get("/admin/ai/feedback").session(login("ADMIN")).param("rating", "NEGATIVE").param("reason", "INCORRECT")
            .param("from", "2026-11-12").param("through", "2026-11-12"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Thông tin sai.")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đánh dấu đã xem xét")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nội dung riêng tư"))));
    }
}
