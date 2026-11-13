package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AdminAiDashboardController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.AdminAiDashboardService;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAiDashboardController.class)
@Import(SecurityConfig.class)
class AdminAiDashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminAiDashboardService dashboard;
    @MockitoBean CartService cartService;
    @MockitoBean CategoryRepository categoryRepository;
    @MockitoBean CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    private MockHttpSession login(String role) {
        User user = new User(); user.setId(1L); user.setRole(role); user.setEmail("test@example.com");
        CustomUserDetails principal = new CustomUserDetails(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test void anonymousAndCustomersCannotOpenDashboard() throws Exception {
        mvc.perform(get("/admin/ai")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dangnhap"));
        mvc.perform(get("/admin/ai").session(login("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(dashboard);
    }

    @Test void adminSeesOnlyAggregatesNAAndMetadataWithWorkingFilterLinks() throws Exception {
        var today = LocalDate.of(2026, 11, 12);
        var recent = new PageImpl<>(List.of(new AdminAiDashboardService.RecentConversation(
            "abcd1234", "12/11/2026 11:00", "12/11/2026 11:05", true)),
            PageRequest.of(0, 10), 12);
        when(dashboard.load(eq("today"), eq(0))).thenReturn(new AdminAiDashboardService.Dashboard(
            "today", today, today, 1, 2, 3, null,
            List.of(new AdminAiDashboardService.DayCount(today, 1, 100)), recent));
        mvc.perform(get("/admin/ai").session(login("ADMIN")).param("period", "today"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/ai-dashboard"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Quản lý Chatbot AI")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("abcd1234")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("period=30d")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private full chat content"))));
        verify(dashboard).load("today", 0);
    }
}
