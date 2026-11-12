package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.SupportController;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupportController.class)
@Import(SecurityConfig.class)
class SupportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SupportService support;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    MockHttpSession session(String role) {
        User user = new User(); user.setId(10L); user.setRole(role); user.setEmail("customer@example.test");
        CustomUserDetails details = new CustomUserDetails(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities())));
        return session;
    }
    CsrfToken csrf(MockHttpSession session) throws Exception {
        when(support.inbox(anyBoolean(), anyInt())).thenReturn(Page.empty());
        var result = mvc.perform(get("/support").session(session)).andExpect(status().isOk()).andReturn();
        return (CsrfToken) result.getRequest().getAttribute("_csrf");
    }
    SupportService.ConversationView conversation() {
        return new SupportService.ConversationView(7L, "<script>alert('subject')</script>", "Khách hàng",
                SupportConversation.Status.WAITING, LocalDateTime.now(), LocalDateTime.now(), 2);
    }

    @Test
    void authenticationAndAdminAuthorizationProtectRoutes() throws Exception {
        mvc.perform(get("/support")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/support/7")).andExpect(status().is3xxRedirection());
        MockHttpSession fake = new MockHttpSession(); fake.setAttribute("currentUser", new User());
        mvc.perform(get("/support").session(fake)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/support").session(session("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(support);
    }

    @Test
    void allMessageMutationsRequireCsrf() throws Exception {
        var customer = session("USER");
        mvc.perform(post("/support").session(customer).param("subject", "Hỗ trợ").param("content", "Tin nhắn"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/support/7/messages").session(customer).param("content", "Tin nhắn")).andExpect(status().isForbidden());
        mvc.perform(post("/support/7/read").session(customer).param("throughId", "1")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/support/7/status").session(session("ADMIN")).param("closed", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(support);
    }

    @Test
    void userCanCreateAndSendButCannotSetSenderOrAdminMode() throws Exception {
        var session = session("USER"); CsrfToken token = csrf(session);
        when(support.create("Hỗ trợ", "Tin đầu")).thenReturn(7L);
        mvc.perform(post("/support").session(session).param(token.getParameterName(), token.getToken())
                        .param("subject", "Hỗ trợ").param("content", "Tin đầu").param("userId", "999"))
                .andExpect(redirectedUrl("/support/7"));
        mvc.perform(post("/support/7/messages").session(session).param(token.getParameterName(), token.getToken())
                        .param("content", "Tin tiếp theo").param("admin", "true").param("senderId", "999"))
                .andExpect(redirectedUrl("/support/7"));
        verify(support).send(7L, "Tin tiếp theo", false);
        mvc.perform(post("/admin/support/7/messages").session(session).param(token.getParameterName(), token.getToken())
                .param("content", "Giả mạo")).andExpect(status().isForbidden());
        verify(support, never()).send(7L, "Giả mạo", true);
    }

    @Test
    void adminCanReplyCloseAndSeeUnreadCounts() throws Exception {
        var session = session("ADMIN"); CsrfToken token = csrf(session);
        when(support.inbox(true, 0)).thenReturn(new PageImpl<>(List.of(conversation())));
        when(support.unreadCount(true)).thenReturn(2L);
        mvc.perform(get("/admin/support").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("2 tin chưa đọc")))
                .andExpect(content().string(containsString("Chờ phản hồi")));
        mvc.perform(post("/admin/support/7/messages").session(session).param(token.getParameterName(), token.getToken())
                        .param("content", "Chúng tôi sẽ hỗ trợ"))
                .andExpect(redirectedUrl("/admin/support/7"));
        verify(support).send(7L, "Chúng tôi sẽ hỗ trợ", true);
        mvc.perform(post("/admin/support/7/status").session(session).param(token.getParameterName(), token.getToken()).param("closed", "true"))
                .andExpect(redirectedUrl("/admin/support/7"));
        verify(support).setClosed(7L, true);
    }

    @Test
    void messageAndSubjectHtmlAreEscapedAndReadMarkerIsCsrfProtected() throws Exception {
        String payload = "<script>alert('message')</script>";
        var history = new SupportService.ThreadView(conversation(), List.of(new SupportService.MessageView(
                12L, payload, true, LocalDateTime.now())), 0, false, false, 12L);
        when(support.thread(7L, false, 0)).thenReturn(history);
        var session = session("USER");
        var result = mvc.perform(get("/support/7").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(payload))))
                .andExpect(content().string(not(containsString(conversation().subject())))).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post("/support/7/read").session(session).param(token.getParameterName(), token.getToken())
                        .param("throughId", "12").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
        verify(support).markRead(7L, 12L, false);
    }

    @Test
    void foreignConversationReturnsVietnamese404AndValidationKeepsDraft() throws Exception {
        var session = session("USER");
        when(support.thread(999L, false, 0)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/support/999").session(session)).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Không tìm thấy hội thoại")));
        CsrfToken token = csrf(session);
        doThrow(new IllegalArgumentException("Tin nhắn phải có từ 1 đến 4000 ký tự.")).when(support).send(7L, " ", false);
        mvc.perform(post("/support/7/messages").session(session).param(token.getParameterName(), token.getToken()).param("content", " "))
                .andExpect(redirectedUrl("/support/7"))
                .andExpect(flash().attribute("error", "Tin nhắn phải có từ 1 đến 4000 ký tự."));
    }
}
