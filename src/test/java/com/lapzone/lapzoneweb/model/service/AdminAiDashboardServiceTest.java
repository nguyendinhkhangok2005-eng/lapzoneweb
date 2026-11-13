package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
class AdminAiDashboardServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);

    @Autowired AiConversationRepository conversations;
    @Autowired AiMessageRepository messages;
    @Autowired AiFeedbackRepository feedback;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;

    private AdminAiDashboardService dashboard() {
        return new AdminAiDashboardService(conversations, messages, new AiFeedbackService(feedback, conversations, messages, users), CLOCK);
    }

    private AiConversation conversation(Instant created, String title) {
        AiConversation c = conversations.saveAndFlush(new AiConversation(null, UUID.randomUUID().toString(), title));
        entityManager.createNativeQuery("update ai_conversations set created_at = :created, updated_at = :created where id = :id")
            .setParameter("created", java.sql.Timestamp.from(created)).setParameter("id", c.getId()).executeUpdate();
        entityManager.flush(); entityManager.clear();
        return conversations.findById(c.getId()).orElseThrow();
    }

    private void message(AiConversation c, String role, String text, String productIds, Instant created) {
        AiMessage m = new AiMessage(c, role, text);
        m.setProductIds(productIds);
        messages.saveAndFlush(m);
        entityManager.createNativeQuery("update ai_messages set created_at = :created where conversation_id = :id and text = :text")
            .setParameter("created", java.sql.Timestamp.from(created))
            .setParameter("id", c.getId()).setParameter("text", text).executeUpdate();
        entityManager.flush(); entityManager.clear();
    }

    @Test void filtersActualCountsAndGroupsByVietnamDayWithZeroDays() {
        Instant afterUtcMidnight = Instant.parse("2026-11-11T18:30:00Z"); // 01:30 on Nov 12 in Vietnam
        AiConversation today = conversation(afterUtcMidnight, "Private medical detail");
        AiConversation threeDaysAgo = conversation(TODAY.minusDays(3).atStartOfDay(ZONE).toInstant(), "Another private title");
        conversation(TODAY.minusDays(8).atStartOfDay(ZONE).toInstant(), "Outside seven days");
        message(today, "user", "private customer question", null, afterUtcMidnight);
        message(today, "model", "private AI response", "", afterUtcMidnight);
        message(threeDaysAgo, "user", "older user question", null, TODAY.minusDays(3).atTime(12, 0).atZone(ZONE).toInstant());

        var current = dashboard().load("today", 0);
        assertEquals(1, current.totalConversations());
        assertEquals(1, current.userMessages()); assertEquals(1, current.aiResponses());
        assertEquals(0L, current.recommendationResponses());
        assertEquals(1, current.conversationsByDay().size());
        assertEquals(TODAY, current.conversationsByDay().getFirst().date());
        assertEquals(1, current.conversationsByDay().getFirst().count());
        assertEquals(100, current.conversationsByDay().getFirst().barPercent());
        assertEquals(8, current.recentConversations().getContent().getFirst().shortId().length());
        assertFalse(current.recentConversations().toString().contains("Private medical detail"));
        assertFalse(current.recentConversations().toString().contains("private customer question"));

        var seven = dashboard().load("7d", 0);
        assertEquals(2, seven.totalConversations()); assertEquals(2, seven.userMessages());
        assertEquals(7, seven.conversationsByDay().size());
        assertEquals(2, seven.conversationsByDay().stream().mapToLong(AdminAiDashboardService.DayCount::count).sum());
        assertEquals(5, seven.conversationsByDay().stream().filter(day -> day.count() == 0).count());
        assertEquals(3, dashboard().load("30d", 0).totalConversations());
    }

    @Test void recommendationActivityIsNAWithoutTrackingAndCountsOnlyStoredReferences() {
        Instant now = TODAY.atTime(12, 0).atZone(ZONE).toInstant();
        AiConversation c = conversation(now, "title");
        message(c, "model", "legacy", null, now);
        assertNull(dashboard().load("today", 0).recommendationResponses());
        message(c, "model", "tracked without products", "", now);
        assertEquals(0L, dashboard().load("today", 0).recommendationResponses());
        message(c, "model", "tracked with products", "101,102", now);
        assertEquals(1L, dashboard().load("today", 0).recommendationResponses());
        message(c, "model", "untracked again", null, now);
        assertEquals(1L, dashboard().load("today", 0).recommendationResponses());
    }

    @Test void recentConversationsArePaginatedAndDoNotExposeTitles() {
        Instant now = TODAY.atTime(12, 0).atZone(ZONE).toInstant();
        for (int i = 0; i < 12; i++) conversation(now.minusSeconds(i), "Secret title " + i);
        var first = dashboard().load("today", 0);
        var second = dashboard().load("today", 1);
        assertEquals(12, first.totalConversations());
        assertEquals(10, first.recentConversations().getNumberOfElements());
        assertEquals(2, second.recentConversations().getNumberOfElements());
        assertEquals(2, first.recentConversations().getTotalPages());
        assertTrue(first.recentConversations().hasNext());
        assertFalse(second.recentConversations().hasNext());
        assertFalse(first.recentConversations().toString().contains("Secret title"));
    }

    @Test void rejectsUnsupportedPeriodsAndInvalidPages() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> dashboard().load("all", 0)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> dashboard().load("7d", -1)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> dashboard().load("7d", 1001)).getStatusCode().value());
    }
}
