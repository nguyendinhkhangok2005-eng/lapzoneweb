package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.SupportService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import(SupportService.class)
class SupportServiceTest {
    @Autowired SupportService support;
    @Autowired UserRepository users;
    @Autowired SupportConversationRepository conversations;
    @Autowired SupportMessageRepository messages;
    @Autowired EntityManager entityManager;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User customer, otherCustomer, admin;

    @BeforeEach
    void setup() {
        customer = user("USER", "Khách hàng một"); otherCustomer = user("USER", "Khách hàng hai");
        admin = user("ADMIN", "Quản trị viên"); login(customer);
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    User user(String role, String name) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setFullName(name); user.setRole(role); return users.saveAndFlush(user);
    }
    void login(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities()));
    }

    @Test
    void customerCreatesAndSendsPersistentMessagesWithServerAssignedOwner() {
        Long id = support.create("  Bảo hành máy  ", "Xin hỗ trợ\nMáy không khởi động.");
        support.send(id, "Thông tin bổ sung", false);
        entityManager.flush(); entityManager.clear();
        var persisted = conversations.findById(id).orElseThrow();
        assertEquals(customer.getId(), persisted.getCustomer().getId());
        assertEquals("Bảo hành máy", persisted.getSubject());
        assertEquals(SupportConversation.Status.WAITING, persisted.getStatus());
        assertNotNull(persisted.getCreatedAt()); assertNotNull(persisted.getUpdatedAt());
        var history = support.thread(id, false, 0).messages();
        assertEquals(2, history.size());
        assertEquals("Xin hỗ trợ\nMáy không khởi động.", history.getFirst().content());
        assertFalse(history.getFirst().adminMessage()); assertNotNull(history.getFirst().sentAt());
        assertEquals(0, support.unreadCount(false));
        login(admin);
        assertEquals(2, support.unreadCount(true));
        assertEquals(2, support.inbox(true, 0).getContent().getFirst().unread());
    }

    @Test
    void adminReplyAndReadReceiptsPersistWithoutMarkingLaterUnseenRepliesRead() {
        Long id = support.create("Đơn hàng", "Cần hỗ trợ");
        login(admin);
        var initial = support.thread(id, true, 0);
        support.markRead(id, initial.throughId(), true);
        assertEquals(0, support.unreadCount(true));
        support.send(id, "LAPZONE đã tiếp nhận.", true);
        entityManager.flush(); entityManager.clear();
        login(customer);
        var firstReply = support.thread(id, false, 0);
        assertEquals(SupportConversation.Status.REPLIED, firstReply.conversation().status());
        assertEquals(1, support.unreadCount(false));
        login(admin); support.send(id, "Xin cung cấp mã đơn hàng.", true);
        login(customer); support.markRead(id, firstReply.throughId(), false);
        entityManager.flush(); entityManager.clear();
        assertEquals(1, support.unreadCount(false));
        support.send(id, "Mã đơn hàng của tôi là 123.", false);
        assertEquals(SupportConversation.Status.WAITING, support.thread(id, false, 0).conversation().status());
        login(admin); assertEquals(1, support.unreadCount(true));
    }

    @Test
    void otherCustomerCannotReadSendMarkReadOrSeeConversationInInbox() {
        Long id = support.create("Riêng tư", "Nội dung riêng tư");
        Long last = support.thread(id, false, 0).throughId();
        login(otherCustomer);
        assertTrue(support.inbox(false, 0).isEmpty());
        assertEquals(0, support.unreadCount(false));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> support.thread(id, false, 0)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> support.send(id, "Không được gửi", false));
        assertThrows(ResponseStatusException.class, () -> support.markRead(id, last, false));
        assertEquals(1, messages.count());
    }

    @Test
    void customerCannotImpersonateAdminEvenWithForgedPrincipalRole() {
        Long id = support.create("Yêu cầu", "Nội dung");
        assertThrows(AccessDeniedException.class, () -> support.inbox(true, 0));
        assertThrows(AccessDeniedException.class, () -> support.send(id, "Giả mạo", true));
        assertThrows(AccessDeniedException.class, () -> support.setClosed(id, true));
        User forged = new User(); forged.setId(customer.getId()); forged.setRole("ADMIN"); login(forged);
        assertThrows(AccessDeniedException.class, () -> support.inbox(true, 0));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> support.create("Ẩn danh", "Không được gửi"));
    }

    @Test
    void closingPreservesHistoryAndPreventsMessagesUntilAdminReopens() {
        Long id = support.create("Yêu cầu", "Nội dung");
        login(admin); support.setClosed(id, true);
        assertThrows(IllegalArgumentException.class, () -> support.send(id, "Đã đóng", true));
        login(customer);
        assertEquals(SupportConversation.Status.CLOSED, support.thread(id, false, 0).conversation().status());
        assertEquals(1, support.thread(id, false, 0).messages().size());
        assertThrows(IllegalArgumentException.class, () -> support.send(id, "Đã đóng", false));
        login(admin); support.setClosed(id, false); support.send(id, "Đã mở lại", true);
        login(customer); assertEquals(2, support.thread(id, false, 0).messages().size());
    }

    @Test
    void invalidInputsCreateNothingAndHtmlRemainsPlainText() {
        assertThrows(IllegalArgumentException.class, () -> support.create(" ", "Nội dung"));
        assertThrows(IllegalArgumentException.class, () -> support.create("x".repeat(121), "Nội dung"));
        assertThrows(IllegalArgumentException.class, () -> support.create("Yêu cầu", " "));
        assertThrows(IllegalArgumentException.class, () -> support.create("Yêu cầu", "x".repeat(4001)));
        assertThrows(IllegalArgumentException.class, () -> support.create("Yêu cầu", "Nội dung\u0000"));
        assertEquals(0, conversations.count()); assertEquals(0, messages.count());
        String payload = "<script>alert('x')</script>";
        Long id = support.create(payload, payload);
        assertEquals(payload, support.thread(id, false, 0).messages().getFirst().content());
    }

    @Test
    void historyIsPagedInChronologicalOrderAndReadOnlyAffectsIncomingMessages() {
        Long id = support.create("Nhiều tin nhắn", "Tin 0");
        for (int i = 1; i <= 51; i++) support.send(id, "Tin " + i, false);
        var latest = support.thread(id, false, 0);
        assertEquals(50, latest.messages().size()); assertTrue(latest.older()); assertFalse(latest.newer());
        assertEquals("Tin 2", latest.messages().getFirst().content());
        assertEquals("Tin 51", latest.messages().getLast().content());
        assertEquals(2, support.thread(id, false, 1).messages().size());
        support.markRead(id, latest.throughId(), false);
        login(admin); assertEquals(52, support.unreadCount(true));
    }
}
