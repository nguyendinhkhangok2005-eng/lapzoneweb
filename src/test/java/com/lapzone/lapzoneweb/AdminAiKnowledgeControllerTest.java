package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AdminAiKnowledgeController;
import com.lapzone.lapzoneweb.model.entity.*;
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
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAiKnowledgeController.class)
@Import(SecurityConfig.class)
class AdminAiKnowledgeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiKnowledgeService knowledge;
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
    AiKnowledgeService.EntryView entry() {
        return new AiKnowledgeService.EntryView(42L, "Hóa đơn", "Nội dung an toàn", "hóa đơn", "FAQ", "Câu hỏi thường gặp", AiKnowledgeEntry.State.DRAFT, 1, null);
    }
    @Test void anonymousAndCustomersCannotAccessKnowledgeManagement() throws Exception {
        mvc.perform(get("/admin/ai/knowledge")).andExpect(status().is3xxRedirection());
        for (String path : List.of("/admin/ai/knowledge", "/admin/ai/knowledge/new", "/admin/ai/knowledge/42", "/admin/ai/knowledge/42/history"))
            mvc.perform(get(path).session(login("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(knowledge);
    }
    @Test void adminFormsAreVietnameseAndEveryWriteRequiresCsrf() throws Exception {
        var admin = login("ADMIN");
        mvc.perform(post("/admin/ai/knowledge").session(admin).param("title", "Mục").param("content", "Nội dung").param("category", "FAQ"))
            .andExpect(status().isForbidden()); verifyNoInteractions(knowledge);
        String html = mvc.perform(get("/admin/ai/knowledge/new").session(admin)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Hướng dẫn chọn mua laptop")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Lưu bản nháp")))
            .andReturn().getResponse().getContentAsString();
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html); assertTrue(matcher.find());
        when(knowledge.create(any())).thenReturn(42L);
        mvc.perform(post("/admin/ai/knowledge").session(admin).param("_csrf", matcher.group(1))
            .param("title", "Mục").param("content", "Nội dung").param("category", "FAQ"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/ai/knowledge/42"));
        verify(knowledge).create(new AiKnowledgeService.Draft("Mục", "Nội dung", "", "FAQ"));
    }
    @Test void rendersListDraftControlsAndEscapedVersionHistory() throws Exception {
        when(knowledge.list(0)).thenReturn(new PageImpl<>(List.of(entry())));
        when(knowledge.get(42L)).thenReturn(entry());
        when(knowledge.history(42L, 0)).thenReturn(new PageImpl<>(List.of(new AiKnowledgeService.VersionView(1, "Tiêu đề", "<script>alert(1)</script>", "Câu hỏi thường gặp", "Tạo mới", "12/11/2026 10:00"))));
        var admin = login("ADMIN");
        mvc.perform(get("/admin/ai/knowledge").session(admin)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Bản nháp")));
        mvc.perform(get("/admin/ai/knowledge/42").session(admin)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Xuất bản bản đã lưu")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"verified\"")));
        mvc.perform(get("/admin/ai/knowledge/42/history").session(admin)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }
}
