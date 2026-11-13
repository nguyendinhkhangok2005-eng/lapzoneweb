package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({AiFeedbackService.class, AiConversationService.class})
class AiFeedbackServiceTest {
    @Autowired AiFeedbackService service;
    @Autowired AiConversationService chat;
    @Autowired AiConversationRepository conversations;
    @Autowired AiMessageRepository messages;
    @Autowired AiFeedbackRepository feedback;
    @Autowired UserRepository users;
    @MockitoBean AiConsultantService consultant;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    AiConversation conversation;
    AiConversationService.Access access;
    @BeforeEach void setup() {
        String key = UUID.randomUUID().toString();
        conversation = conversations.saveAndFlush(new AiConversation(null, key, "Tiêu đề riêng tư"));
        access = new AiConversationService.Access(null, key);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    AiMessage message(String role) { return messages.saveAndFlush(new AiMessage(conversation, role, "Nội dung riêng tư không được chia sẻ")); }
    AiFeedbackService.Submission negative(String reason) { return new AiFeedbackService.Submission("NEGATIVE", reason); }
    int code(Runnable action) { return assertThrows(ResponseStatusException.class, action::run).getStatusCode().value(); }

    @Test void bindsCorrectMessageAndConversationAndMakesRetriesIdempotent() {
        AiMessage m = message("model");
        var vote = service.submit(conversation.getId(), m.getId(), access, negative("INCORRECT"));
        assertEquals(m.getId(), vote.messageId());
        assertEquals(vote, service.submit(conversation.getId(), m.getId(), access, negative("INCORRECT")));
        assertEquals(1, feedback.count());
        assertEquals(conversation.getId(), feedback.findByMessageId(m.getId()).orElseThrow().getConversation().getId());
        assertEquals(409, code(() -> service.submit(conversation.getId(), m.getId(), access, new AiFeedbackService.Submission("POSITIVE", null))));
        assertEquals(1, service.votes(conversation.getId(), access).size());
    }

    @Test void rejectsOtherGuestsOwnersWrongConversationAndUserMessages() {
        AiMessage ai = message("model");
        assertEquals(404, code(() -> service.submit(conversation.getId(), ai.getId(), new AiConversationService.Access(null, "other"), negative("OTHER"))));
        assertEquals(404, code(() -> service.votes(conversation.getId(), new AiConversationService.Access(999L, null))));
        AiConversation other = conversations.saveAndFlush(new AiConversation(null, access.guestKey(), "Cùng chủ"));
        assertEquals(404, code(() -> service.submit(other.getId(), ai.getId(), access, negative("OTHER"))));
        AiMessage user = message("user");
        assertEquals(404, code(() -> service.submit(conversation.getId(), user.getId(), access, negative("OTHER"))));
        assertEquals(0, feedback.count());
    }

    @Test void validatesVietnameseReasonCodesAndRequiresNegativeReasons() {
        AiMessage ai = message("model");
        assertEquals(400, code(() -> service.submit(conversation.getId(), ai.getId(), access, negative(null))));
        assertEquals(400, code(() -> service.submit(conversation.getId(), ai.getId(), access, negative("FAKE"))));
        assertEquals(400, code(() -> service.submit(conversation.getId(), ai.getId(), access, new AiFeedbackService.Submission("POSITIVE", "OTHER"))));
        for (AiFeedback.Reason reason : AiFeedback.Reason.values()) {
            service.submit(conversation.getId(), message("model").getId(), access, negative(reason.name()));
        }
        assertEquals(5, feedback.count());
    }

    @Test void authenticatedOwnerCanVoteButAnotherAccountCannotModifyOrReadIt() {
        User owner = users.saveAndFlush(new User(null, "Chủ hội thoại", UUID.randomUUID()+"@test.local", null, "unused", null, "USER"));
        AiConversation owned = conversations.saveAndFlush(new AiConversation(owner, null, "Riêng tư"));
        AiMessage ai = messages.saveAndFlush(new AiMessage(owned, "model", "Nội dung riêng"));
        var ownAccess = new AiConversationService.Access(owner.getId(), null);
        service.submit(owned.getId(), ai.getId(), ownAccess, new AiFeedbackService.Submission("POSITIVE", null));
        var other = new AiConversationService.Access(owner.getId() + 999, null);
        assertEquals(404, code(() -> service.submit(owned.getId(), ai.getId(), other, negative("OTHER"))));
        assertEquals(404, code(() -> service.votes(owned.getId(), other)));
        assertEquals("POSITIVE", service.votes(owned.getId(), ownAccess).getFirst().rating());
    }

    @Test void limitsNewSubmissionsAndStillAcceptsIdenticalRetry() {
        AiMessage first = message("model");
        service.submit(conversation.getId(), first.getId(), access, negative("OTHER"));
        for (int i = 1; i < 20; i++) service.submit(conversation.getId(), message("model").getId(), access, negative("OTHER"));
        AiMessage last = message("model");
        assertEquals(429, code(() -> service.submit(conversation.getId(), last.getId(), access, negative("OTHER"))));
        assertNotNull(service.submit(conversation.getId(), first.getId(), access, negative("OTHER")));
        assertEquals(20, feedback.count());
    }

    @Test void filtersCountsAndReviewDoNotExposePrivateContents() {
        AiMessage ai = message("model");
        service.submit(conversation.getId(), ai.getId(), access, negative("BAD_RECOMMENDATION"));
        service.submit(conversation.getId(), message("model").getId(), access, new AiFeedbackService.Submission("POSITIVE", null));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var summary = service.summary(today.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        assertEquals(1, summary.positive()); assertEquals(1, summary.negative());
        assertEquals(1, summary.recentIssues().size());
        var page = service.reports("NEGATIVE", "BAD_RECOMMENDATION", today, today, 0);
        assertEquals(1, page.reports().getTotalElements());
        assertFalse(page.toString().contains("Nội dung riêng tư")); assertFalse(page.toString().contains("Tiêu đề riêng tư"));
        assertEquals(0, service.reports("POSITIVE", "BAD_RECOMMENDATION", today, today, 0).reports().getTotalElements());
        assertEquals(0, service.reports("", "", today.minusDays(2), today.minusDays(1), 0).reports().getTotalElements());
        Long id = feedback.findByMessageId(ai.getId()).orElseThrow().getId();
        assertEquals(403, code(() -> service.review(id)));
        User admin = users.saveAndFlush(new User(null, "Quản trị", UUID.randomUUID()+"@test.local", null, "unused", null, "ADMIN"));
        CustomUserDetails principal = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        service.review(id);
        Instant reviewed = feedback.findById(id).orElseThrow().getReviewedAt();
        service.review(id);
        assertEquals(reviewed, feedback.findById(id).orElseThrow().getReviewedAt());
        assertTrue(service.reports("NEGATIVE", "", today, today, 0).reports().getContent().getFirst().reviewed());
    }

    @Test void resetAndDeleteRemoveFeedbackWithoutBreakingExistingHistoryLifecycle() {
        service.submit(conversation.getId(), message("model").getId(), access, negative("OTHER"));
        chat.reset(conversation.getId(), access);
        assertEquals(0, feedback.count());
        service.submit(conversation.getId(), message("model").getId(), access, negative("OTHER"));
        chat.delete(conversation.getId(), access);
        conversations.flush();
        assertEquals(0, feedback.count());
    }
}
