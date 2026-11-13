package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AdminAiInspectionController;
import com.lapzone.lapzoneweb.model.dto.AiProductDTO;
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
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAiInspectionController.class)
@Import(SecurityConfig.class)
class AdminAiInspectionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminAiInspectionService inspector;
    @MockitoBean AiInspectionRunStore store;
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
    @Test void onlyAdminsCanViewOrRunTheInspector() throws Exception {
        mvc.perform(get("/admin/ai/inspector")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/ai/inspector").session(login("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/ai/inspector/1").session(login("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/ai/inspector/run").session(login("ADMIN")).param("question", "Laptop").param("requestToken", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(inspector, store);
    }
    @Test void loadingPresetsNeverRunsAiAndPostRequiresExplicitCsrfProtectedAction() throws Exception {
        when(store.list(0)).thenReturn(Page.empty());
        var admin = login("ADMIN");
        String html = mvc.perform(get("/admin/ai/inspector").session(admin).param("preset", "OFFICE"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Văn phòng")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Chạy một bài kiểm tra")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Lập trình")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đồ họa")))
            .andReturn().getResponse().getContentAsString();
        verifyNoInteractions(inspector);
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html); assertTrue(csrf.find());
        String token = UUID.randomUUID().toString();
        when(inspector.run("Laptop", "CUSTOM", token)).thenReturn(7L);
        mvc.perform(post("/admin/ai/inspector/run").session(admin).param("_csrf", csrf.group(1))
                .param("question", "Laptop").param("requestToken", token)).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/ai/inspector/7"));
        verify(inspector, times(1)).run("Laptop", "CUSTOM", token);
    }
    @Test void resultsEscapeReturnedTextShowAllThreeStatusesAndDoNotLoadUnverifiedImages() throws Exception {
        List<AiRecommendationValidator.Check> checks = List.of(
            new AiRecommendationValidator.Check("Mã sản phẩm", AiRecommendationValidator.Status.PASS, "1", "1", "Đã đối chiếu"),
            new AiRecommendationValidator.Check("Giá", AiRecommendationValidator.Status.FAIL, "100", "1", "Sai lệch"),
            new AiRecommendationValidator.Check("Phù hợp", AiRecommendationValidator.Status.NOT_VERIFIED, "Bằng chứng", "Chưa có", "Chưa xác minh"));
        AiProductDTO p = new AiProductDTO(1L, "<script>alert(1)</script>", 1d, "https://unverified/image.jpg", Map.of(), 1, true, "/product_detail?id=1");
        var result = new AiRecommendationValidator.Result("<script>alert(1)</script>", List.of(new AiRecommendationValidator.ProductReport(p, true, null, checks)), checks);
        var run = new AiInspectionRunStore.RunView(1L, "Câu hỏi", "Tùy chỉnh", "Đã hoàn tất", "12/11/2026 10:00:00", "12/11/2026 10:00:01");
        when(store.get(1L)).thenReturn(new AiInspectionRunStore.Detail(run, result, null));
        mvc.perform(get("/admin/ai/inspector/1").session(login("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("PASS")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("FAIL")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("NOT VERIFIED")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://unverified/image.jpg"))));
        verifyNoInteractions(inspector);
    }
}
