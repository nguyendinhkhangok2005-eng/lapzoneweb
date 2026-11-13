package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.AiConversation;
import com.lapzone.lapzoneweb.model.repository.AiConversationRepository;
import com.lapzone.lapzoneweb.model.repository.AiMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AdminAiDashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int PAGE_SIZE = 10;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final Clock clock;
    private final AiFeedbackService feedback;

    @Autowired
    public AdminAiDashboardService(AiConversationRepository conversations, AiMessageRepository messages, AiFeedbackService feedback) {
        this(conversations, messages, feedback, Clock.system(ZONE));
    }

    // Package-visible clock seam keeps date-boundary tests deterministic.
    AdminAiDashboardService(AiConversationRepository conversations, AiMessageRepository messages, AiFeedbackService feedback, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
        this.feedback = feedback;
    }

    public record DayCount(LocalDate date, long count, int barPercent) {}
    public record RecentConversation(String shortId, String createdAt, String updatedAt, boolean guest) {}
    public record Dashboard(String period, LocalDate from, LocalDate through,
                            long totalConversations, long userMessages, long aiResponses,
                            Long recommendationResponses, List<DayCount> conversationsByDay,
                            Page<RecentConversation> recentConversations, AiFeedbackService.Summary feedback) {
        public Dashboard(String period, LocalDate from, LocalDate through, long totalConversations, long userMessages,
                         long aiResponses, Long recommendationResponses, List<DayCount> days, Page<RecentConversation> recent) {
            this(period, from, through, totalConversations, userMessages, aiResponses, recommendationResponses, days, recent,
                new AiFeedbackService.Summary(0, 0, List.of()));
        }
    }

    public Dashboard load(String period, int page) {
        int days = switch (period) {
            case "today" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown date filter");
        };
        if (page < 0 || page > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate firstDay = today.minusDays(days - 1L);
        Instant start = firstDay.atStartOfDay(ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();

        Page<AiConversation> recent = conversations
            .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                start, end, PageRequest.of(page, PAGE_SIZE));
        Map<LocalDate, Long> dailyCounts = new HashMap<>();
        for (Object[] row : conversations.countCreatedByVietnamDay(start, end)) {
            LocalDate day = toLocalDate(row[0]);
            dailyCounts.put(day, ((Number) row[1]).longValue());
        }
        long max = dailyCounts.values().stream().mapToLong(value -> value).max().orElse(0);
        List<DayCount> daysWithZeros = firstDay.datesUntil(today.plusDays(1))
            .map(day -> new DayCount(day, dailyCounts.getOrDefault(day, 0L),
                max == 0 ? 0 : (int) Math.round(100.0 * dailyCounts.getOrDefault(day, 0L) / max)))
            .toList();

        long userCount = 0, modelCount = 0;
        for (Object[] row : messages.countByRoleInPeriod(start, end)) {
            if ("user".equals(row[0])) userCount = ((Number) row[1]).longValue();
            if ("model".equals(row[0])) modelCount = ((Number) row[1]).longValue();
        }
        AiMessageRepository.RecommendationCoverage coverage = messages.recommendationCoverageInPeriod(start, end);
        Long recommendations = coverage != null && coverage.getTracked() > 0
            ? Objects.requireNonNullElse(coverage.getRecommended(), 0L) : null;

        return new Dashboard(period, firstDay, today, recent.getTotalElements(), userCount,
            modelCount, recommendations, daysWithZeros, recent.map(c -> new RecentConversation(
                c.getId().substring(0, Math.min(8, c.getId().length())),
                format(c.getCreatedAt()), format(c.getUpdatedAt()), c.isGuest())), feedback.summary(start, end));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDateTime time) return time.toLocalDate();
        throw new IllegalStateException("Unexpected database day type: " + value.getClass());
    }

    private String format(Instant instant) {
        return TIME_FORMAT.format(instant.atZone(ZONE));
    }
}
