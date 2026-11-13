package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controllers, security, AI routing and support persistence; no external AI call is needed. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai_support_handoff;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
class AiSupportHandoffIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AiConversationRepository conversations;
    @Autowired AiMessageRepository aiMessages;
    @Autowired SupportConversationRepository supportConversations;
    @Autowired SupportMessageRepository supportMessages;
    @Autowired AiConversationService ai;
    @Autowired AiSupportHandoffService handoff;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    final ObjectMapper json = new ObjectMapper();
    User customer, other, admin;
    MockHttpSession customerSession, otherSession, adminSession;

    @BeforeEach void setup() {
        customer = user("USER"); other = user("USER"); admin = user("ADMIN");
        customerSession = session(customer); otherSession = session(other); adminSession = session(admin);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    User user(String role) {
        User u = new User(); u.setFullName("Khách kiểm thử"); u.setRole(role); u.setEmail(UUID.randomUUID() + "@test.local");
        return users.saveAndFlush(u);
    }
    MockHttpSession session(User u) {
        var p = new CustomUserDetails(u); var s = new MockHttpSession();
        s.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities())));
        return s;
    }
    String createAi(User u) { return ai.create(new AiConversationService.Access(u.getId(), null), null).id(); }
    AiMessage message(String id, String role, String text) {
        return aiMessages.saveAndFlush(new AiMessage(conversations.findById(id).orElseThrow(), role, text));
    }
    Long supportQuestion(String id) throws Exception {
        String response = mvc.perform(post("/api/ai/chat").session(customerSession).contentType("application/json")
            .content(json.writeValueAsString(Map.of("message", "Đơn hàng của tôi chưa giao, xin gặp nhân viên", "conversationId", id))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.supportOffered").value(true)).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("messageId").asLong();
    }
    String path(String id, Long messageId) { return "/api/ai/conversations/" + id + "/messages/" + messageId + "/support"; }
    JsonNode preview(String path, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get(path).session(session)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn().getResponse().getContentAsString());
    }
    ResultActions submit(String path, MockHttpSession session, JsonNode token, Object body) throws Exception {
        return mvc.perform(post(path).session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content(json.writeValueAsString(body)));
    }

    @Test void completeConsentHandoffAdminReplyAndCustomerStatusUseTheExistingSupportThread() throws Exception {
        String id = createAi(customer), unrelated = createAi(customer);
        AiMessage agreed = message(id, "user", "Mã đơn hàng cần hỗ trợ: 123");
        message(id, "model", "Nội dung AI không đồng ý chia sẻ");
        message(unrelated, "user", "Bí mật hội thoại khác");
        String path = path(id, supportQuestion(id));
        var token = preview(path, customerSession);
        assertEquals(2, token.at("/preview/context").size());
        assertTrue(token.at("/preview/request").isNull()); assertEquals(0, supportConversations.count());
        submit(path, customerSession, token, Map.of("confirmed", false, "contextMessageIds", List.of(agreed.getId())))
            .andExpect(status().isBadRequest());
        assertEquals(0, supportConversations.count());
        var submission = Map.of("confirmed", true, "contextMessageIds", List.of(agreed.getId()));
        var sent = json.readTree(submit(path, customerSession, token, submission).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WAITING")).andReturn().getResponse().getContentAsString());
        Long supportId = sent.get("id").asLong();
        submit(path, customerSession, token, submission).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(supportId));
        em.flush(); em.clear(); assertEquals(1, supportConversations.count()); assertEquals(1, supportMessages.count());
        var stored = supportMessages.findByConversationIdOrderByIdDesc(supportId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        assertTrue(stored.getContent().contains("Đơn hàng của tôi chưa giao"));
        assertTrue(stored.getContent().contains(agreed.getText()));
        assertFalse(stored.getContent().contains("Nội dung AI không đồng ý"));
        assertFalse(stored.getContent().contains("Bí mật hội thoại khác"));
        var adminView = mvc.perform(get("/admin/support/" + supportId).session(adminSession)).andExpect(status().isOk())
            .andExpect(content().string(containsString(agreed.getText()))).andReturn();
        CsrfToken adminToken = (CsrfToken) adminView.getRequest().getAttribute("_csrf");
        mvc.perform(post("/admin/support/" + supportId + "/messages").session(adminSession)
            .param(adminToken.getParameterName(), adminToken.getToken()).param("content", "Nhân viên đã tiếp nhận, chúng tôi đang kiểm tra đơn 123."))
            .andExpect(redirectedUrl("/admin/support/" + supportId));
        var updated = preview(path, customerSession);
        assertEquals("REPLIED", updated.at("/preview/request/status").asText());
        assertTrue(updated.at("/preview/request/latestReply").asText().contains("đang kiểm tra đơn 123"));
        mvc.perform(get("/support/" + supportId).session(customerSession)).andExpect(status().isOk())
            .andExpect(content().string(containsString("đang kiểm tra đơn 123")));
        mvc.perform(post("/admin/support/" + supportId + "/status").session(adminSession)
            .param(adminToken.getParameterName(), adminToken.getToken()).param("closed", "true"))
            .andExpect(status().is3xxRedirection());
        assertEquals("CLOSED", preview(path, customerSession).at("/preview/request/status").asText());
        assertTrue(ai.retrieve(id, new AiConversationService.Access(customer.getId(), null)).messages().getLast().supportOffered());
        assertEquals(2, ai.retrieve(id, new AiConversationService.Access(customer.getId(), null)).messages().stream()
            .filter(m -> m.supportOffered() || "Đơn hàng của tôi chưa giao, xin gặp nhân viên".equals(m.text())).count());
    }

    @Test void authenticationOwnershipCsrfAndAdminPermissionsAreEnforced() throws Exception {
        String id = createAi(customer), path = path(id, supportQuestion(id));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get(path).session(adminSession)).andExpect(status().isNotFound());
        mvc.perform(post(path).session(customerSession).contentType("application/json").content("{\"confirmed\":true}"))
            .andExpect(status().isForbidden());
        var token = preview(path, customerSession);
        submit(path, customerSession, token, Map.of("confirmed", true)).andExpect(status().isOk());
        var otherToken = json.readTree(mvc.perform(get("/api/ai/feedback/csrf").session(otherSession)).andReturn().getResponse().getContentAsString());
        submit(path, otherSession, otherToken, Map.of("confirmed", true)).andExpect(status().isNotFound());
        Long supportId = supportConversations.findAll().getFirst().getId();
        mvc.perform(get("/support/" + supportId).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/support/" + supportId).session(customerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/support/" + supportId + "/messages").session(customerSession)
            .header(token.get("headerName").asText(), token.get("token").asText()).param("content", "Giả mạo"))
            .andExpect(status().isForbidden());
        assertEquals(1, supportMessages.count());
    }

    @Test void rejectsForeignFutureDuplicateAndOversizedContextAndUnofferedMessages() throws Exception {
        String id = createAi(customer), otherId = createAi(customer);
        AiMessage large = message(id, "model", "x".repeat(4000));
        AiMessage foreign = message(otherId, "user", "Không được chia sẻ");
        Long answerId = supportQuestion(id); String path = path(id, answerId); var token = preview(path, customerSession);
        AiMessage future = message(id, "user", "Tin nhắn được gửi sau khi xác nhận");
        for (List<Long> selection : List.of(List.of(foreign.getId()), List.of(future.getId()),
                List.of(large.getId(), large.getId()), List.of(large.getId()))) {
            submit(path, customerSession, token, Map.of("confirmed", true, "contextMessageIds", selection)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(id, future.getId())).session(customerSession)).andExpect(status().isNotFound());
        AiMessage ordinary = message(id, "model", "Câu trả lời sản phẩm thông thường");
        mvc.perform(get(path(id, ordinary.getId())).session(customerSession)).andExpect(status().isNotFound());
        assertEquals(0, supportConversations.count());
    }

    @Test void noOptionalContextIsSharedByDefaultAndDeletingAiHistoryPreservesTheSupportRequest() throws Exception {
        String id = createAi(customer); message(id, "user", "Tin nhắn riêng tư <script>secret</script>");
        message(id, "model", "Phản hồi trước");
        Long answerId = supportQuestion(id); String path = path(id, answerId); var token = preview(path, customerSession);
        var sent = json.readTree(submit(path, customerSession, token, Map.of("confirmed", true))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Long supportId = sent.get("id").asLong();
        var stored = supportMessages.findByConversationIdOrderByIdDesc(supportId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        assertFalse(stored.getContent().contains("Tin nhắn riêng tư")); assertFalse(stored.getContent().contains("Phản hồi trước"));
        ai.delete(id, new AiConversationService.Access(customer.getId(), null)); em.flush(); em.clear();
        mvc.perform(get("/support/" + supportId).session(customerSession)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Đơn hàng của tôi chưa giao")));
        mvc.perform(get(path).session(customerSession)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmedSubmissionsCreateOnlyOneExistingSupportThread() throws Exception {
        String id = createAi(customer);
        Long answerId = ai.chatWithProducts(id, new AiConversationService.Access(customer.getId(), null),
            "Đơn hàng của tôi chưa giao").messageId();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> action = () -> {
                var principal = new CustomUserDetails(customer);
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
                try {
                    ready.countDown(); assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return handoff.submit(id, answerId, new AiSupportHandoffService.Submission(true, List.of())).id();
                } finally { SecurityContextHolder.clearContext(); }
            };
            var first = pool.submit(action); var second = pool.submit(action);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            assertEquals(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, supportConversations.count()); assertEquals(1, supportMessages.count());
        } finally {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                em.createQuery("delete from SupportMessage m where m.conversation.customer.id = :owner").setParameter("owner", customer.getId()).executeUpdate();
                em.createQuery("delete from SupportConversation c where c.customer.id = :owner").setParameter("owner", customer.getId()).executeUpdate();
                ai.delete(id, new AiConversationService.Access(customer.getId(), null));
                users.deleteAll(List.of(customer, other, admin));
            });
        }
    }
}
