package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AdminAiSettingsController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAiSettingsController.class)
@Import(SecurityConfig.class)
class AdminAiSettingsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiResponseSettingsService settings;
    @MockitoBean CartService cart;
    @MockitoBean CategoryRepository categories;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    MockHttpSession login(String role) {
        User user = new User(); user.setId(1L); user.setRole(role);
        var principal = new CustomUserDetails(user); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var session = new MockHttpSession(); session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context); return session;
    }
    @Test void settingsEndpointsRequireAdminAndWritesRequireCsrf() throws Exception {
        mvc.perform(get("/admin/ai/settings")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/ai/settings").session(login("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/ai/settings/history").session(login("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/ai/settings").session(login("USER")).param("style", "FRIENDLY")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/ai/settings").session(login("ADMIN")).param("style", "FRIENDLY")).andExpect(status().isForbidden());
        verifyNoInteractions(settings);
    }
    @Test void previewIsVietnameseAndNeverSavesOrInvokesTheConsultant() throws Exception {
        when(settings.current()).thenReturn(AiResponseSettingsService.Snapshot.defaults());
        when(settings.asInput(any())).thenReturn(new AiResponseSettingsService.Input("FRIENDLY", "MEDIUM", "3", false, false, null));
        when(settings.history(0)).thenReturn(Page.empty());
        var admin = login("ADMIN");
        String html = mvc.perform(get("/admin/ai/settings").session(admin)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Xem trước, không gọi AI")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Khôi phục cấu hình mặc định")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Chuyên nghiệp")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("GEMINI_API_KEY"))))
            .andReturn().getResponse().getContentAsString();
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html); assertTrue(csrf.find());
        when(settings.preview(any())).thenReturn("Bản xem trước bằng tiếng Việt.");
        mvc.perform(post("/admin/ai/settings/preview").session(admin).param("_csrf", csrf.group(1))
                .param("style", "PROFESSIONAL").param("responseLength", "DETAILED")
                .param("maxRecommendations", "5").param("highlightPromotions", "true")
                .param("suggestRelatedProducts", "true"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Bản xem trước bằng tiếng Việt.")));
        verify(settings).preview(argThat(input -> input.style().equals("PROFESSIONAL") && input.highlightPromotions()
            && input.suggestRelatedProducts() && input.maxRecommendations().equals("5")));
        verify(settings, never()).save(any());
        verify(settings, never()).reset(any());
    }
    @Test void saveResetAndHistoryUseExplicitAdminActions() throws Exception {
        when(settings.current()).thenReturn(AiResponseSettingsService.Snapshot.defaults());
        when(settings.asInput(any())).thenReturn(new AiResponseSettingsService.Input("FRIENDLY", "MEDIUM", "3", false, false, null));
        when(settings.history(anyInt())).thenReturn(Page.empty());
        var admin = login("ADMIN");
        String page = mvc.perform(get("/admin/ai/settings").session(admin)).andReturn().getResponse().getContentAsString();
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page); assertTrue(csrf.find());
        mvc.perform(post("/admin/ai/settings").session(admin).param("_csrf", csrf.group(1))
                .param("style", "CONCISE").param("responseLength", "SHORT")
                .param("maxRecommendations", "1").param("highlightPromotions", "false")
                .param("suggestRelatedProducts", "false"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/ai/settings"));
        verify(settings).save(argThat(input -> input.style().equals("CONCISE") && input.maxRecommendations().equals("1")));
        mvc.perform(post("/admin/ai/settings/reset").session(admin).param("_csrf", csrf.group(1)))
            .andExpect(status().is3xxRedirection());
        verify(settings).reset(null);
        mvc.perform(get("/admin/ai/settings/history").session(admin).param("page", "2")).andExpect(status().isOk());
        verify(settings).history(2);
    }
}
