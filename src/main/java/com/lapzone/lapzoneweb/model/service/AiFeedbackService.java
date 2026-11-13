package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.entity.AiFeedback.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AiFeedbackService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AiFeedbackRepository feedback;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final UserRepository users;

    public AiFeedbackService(AiFeedbackRepository feedback, AiConversationRepository conversations,
                             AiMessageRepository messages, UserRepository users) {
        this.feedback = feedback; this.conversations = conversations; this.messages = messages; this.users = users;
    }
    public record Submission(String rating, String reason) {}
    public record Vote(Long messageId, String rating, String reason) {}
    // Deliberately no message text, title, name, email or session key in admin reports.
    public record Report(Long id, Long messageId, String conversationReference, String rating,
                         String reason, String createdAt, String reviewedAt, boolean reviewed) {}
    public record Summary(long positive, long negative, List<Report> recentIssues) {}
    public record ReportPage(String rating, String reason, LocalDate from, LocalDate through, Page<Report> reports) {}

    @Transactional
    public Vote submit(String conversationId, Long messageId, AiConversationService.Access access, Submission request) {
        if (request == null) throw bad("Vui lòng chọn mức đánh giá.");
        Rating rating = parse(Rating.class, request.rating(), false);
        Reason reason = parse(Reason.class, request.reason(), true);
        if (rating == Rating.NEGATIVE && reason == null) throw bad("Vui lòng chọn lý do đánh giá chưa hài lòng.");
        if (rating == Rating.POSITIVE && reason != null) throw bad("Đánh giá tích cực không cần lý do báo lỗi.");
        AiConversation conversation = conversations.findAccessibleByIdForUpdate(conversationId, access.ownerId(), access.guestKey())
            .orElseThrow(this::missing);
        AiMessage message = messages.findByIdAndConversationId(messageId, conversationId)
            .filter(m -> "model".equals(m.getRole())).orElseThrow(this::missing);
        Optional<AiFeedback> previous = feedback.findByMessageId(messageId);
        if (previous.isPresent()) {
            AiFeedback saved = previous.get();
            if (saved.getRating() != rating || saved.getReason() != reason)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tin nhắn này đã được đánh giá. Không thể thay đổi đánh giá đã gửi.");
            return vote(saved); // Identical retries are idempotent and never create a second report.
        }
        if (feedback.countByConversationIdAndCreatedAtGreaterThanEqual(conversationId, Instant.now().minusSeconds(60)) >= 20)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi đánh giá quá nhanh. Vui lòng đợi một phút rồi thử lại.");
        return vote(feedback.save(new AiFeedback(message, conversation, rating, reason)));
    }

    public List<Vote> votes(String conversationId, AiConversationService.Access access) {
        conversations.findAccessibleById(conversationId, access.ownerId(), access.guestKey()).orElseThrow(this::missing);
        return feedback.findByConversationId(conversationId).stream().map(this::vote).toList();
    }

    public Summary summary(Instant start, Instant end) {
        long positive = 0, negative = 0;
        for (Object[] row : feedback.countRatings(start, end)) {
            if (row[0] == Rating.POSITIVE) positive = ((Number) row[1]).longValue();
            if (row[0] == Rating.NEGATIVE) negative = ((Number) row[1]).longValue();
        }
        List<Report> recent = feedback.findByRatingAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Rating.NEGATIVE, start, end, PageRequest.of(0, 5)).stream().map(this::report).toList();
        return new Summary(positive, negative, recent);
    }

    public ReportPage reports(String ratingText, String reasonText, LocalDate from, LocalDate through, int page) {
        if (from == null || through == null || from.isAfter(through) || from.plusDays(365).isBefore(through)
                || page < 0 || page > 1000) throw bad("Khoảng ngày hoặc số trang không hợp lệ. Vui lòng chọn tối đa 366 ngày.");
        Rating rating = parse(Rating.class, ratingText, true);
        Reason reason = parse(Reason.class, reasonText, true);
        Page<Report> reports = feedback.reports(rating, reason, from.atStartOfDay(ZONE).toInstant(),
            through.plusDays(1).atStartOfDay(ZONE).toInstant(), PageRequest.of(page, 20)).map(this::report);
        return new ReportPage(rating == null ? "" : rating.name(), reason == null ? "" : reason.name(), from, through, reports);
    }

    @Transactional
    public void review(Long id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails principal)
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem xét báo cáo.");
        User admin = users.findById(principal.getUser().getId()).filter(u -> "ADMIN".equals(u.getRole()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị không còn hợp lệ."));
        feedback.lockForReview(id).orElseThrow(this::missing).review(admin);
    }

    private Vote vote(AiFeedback f) {
        return new Vote(f.getMessage().getId(), f.getRating().name(), f.getReason() == null ? null : f.getReason().name());
    }
    private Report report(AiFeedback f) {
        return new Report(f.getId(), f.getMessage().getId(), f.getConversation().getId().substring(0, 8),
            f.getRating().getLabel(), f.getReason() == null ? "Không áp dụng" : f.getReason().getLabel(),
            format(f.getCreatedAt()), f.getReviewedAt() == null ? "Chưa xem xét" : format(f.getReviewedAt()), f.getReviewedAt() != null);
    }
    private String format(Instant time) { return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(time.atZone(ZONE)); }
    private <E extends Enum<E>> E parse(Class<E> type, String text, boolean optional) {
        if (text == null || text.isBlank()) { if (optional) return null; throw bad("Vui lòng chọn mức đánh giá."); }
        try { return Enum.valueOf(type, text); }
        catch (IllegalArgumentException ex) { throw bad("Mức đánh giá hoặc lý do không hợp lệ."); }
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn hoặc đánh giá."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
