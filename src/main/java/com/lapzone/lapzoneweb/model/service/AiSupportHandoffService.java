package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.AiMessage;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/** Consent boundary only. SupportService remains the sole messaging/status implementation. */
@Service
@Transactional(readOnly = true)
public class AiSupportHandoffService {
    private final AiConversationRepository aiConversations;
    private final AiMessageRepository aiMessages;
    private final SupportConversationRepository supportConversations;
    private final UserRepository users;
    private final SupportService support;

    public AiSupportHandoffService(AiConversationRepository aiConversations, AiMessageRepository aiMessages,
            SupportConversationRepository supportConversations, UserRepository users, SupportService support) {
        this.aiConversations = aiConversations; this.aiMessages = aiMessages;
        this.supportConversations = supportConversations; this.users = users; this.support = support;
    }

    public record SharedMessage(Long id, String role, String text, Instant createdAt) {}
    public record RequestStatus(Long id, String status, String statusLabel, String url,
                                String latestReply, LocalDateTime repliedAt) {}
    public record Preview(SharedMessage question, List<SharedMessage> context, RequestStatus request) {}
    public record Submission(Boolean confirmed, List<Long> contextMessageIds) {}
    private record Source(AiMessage question, List<AiMessage> context) {}

    public Preview preview(String conversationId, Long messageId) {
        Long ownerId = ownerId();
        Source source = source(conversationId, messageId, ownerId, false);
        var existing = supportConversations.findByAiSourceConversationIdAndAiSourceMessageIdAndCustomerId(
            conversationId, messageId, ownerId);
        return new Preview(shared(source.question()), source.context().stream().map(this::shared).toList(),
            existing.map(c -> status(c.getId())).orElse(null));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RequestStatus submit(String conversationId, Long messageId, Submission submission) {
        Long ownerId = ownerId();
        if (submission == null || !Boolean.TRUE.equals(submission.confirmed()))
            throw bad("Vui lòng xác nhận nội dung muốn chia sẻ trước khi gửi yêu cầu.");
        // Same lock as AI sends/reset/delete: retries and concurrent tabs cannot create duplicate threads.
        Source source = source(conversationId, messageId, ownerId, true);
        var existing = supportConversations.findByAiSourceConversationIdAndAiSourceMessageIdAndCustomerId(
            conversationId, messageId, ownerId);
        if (existing.isPresent()) return status(existing.get().getId());

        List<Long> selected = submission.contextMessageIds() == null ? List.of() : submission.contextMessageIds();
        if (selected.size() > 6 || selected.stream().anyMatch(Objects::isNull) || new HashSet<>(selected).size() != selected.size())
            throw bad("Danh sách tin nhắn chia sẻ không hợp lệ.");
        Map<Long, AiMessage> allowed = new HashMap<>();
        source.context().forEach(m -> allowed.put(m.getId(), m));
        if (!allowed.keySet().containsAll(selected))
            throw bad("Chỉ được chia sẻ các tin nhắn đã hiển thị trong phần xác nhận của hội thoại này.");
        StringBuilder body = new StringBuilder("Yêu cầu chuyển từ trợ lý AI\n\nCâu hỏi cần hỗ trợ:\n")
            .append(source.question().getText());
        for (AiMessage message : source.context()) {
            if (selected.contains(message.getId())) body.append("\n\n")
                .append("user".equals(message.getRole()) ? "Khách hàng" : "Trợ lý AI")
                .append(":\n").append(message.getText());
        }
        if (body.length() > 4000)
            throw bad("Nội dung chia sẻ vượt 4.000 ký tự. Vui lòng chọn ít tin nhắn bổ sung hơn.");
        Long id = support.create("Hỗ trợ từ chatbot AI", body.toString());
        var thread = supportConversations.findById(id).orElseThrow();
        thread.setAiSourceConversationId(conversationId); thread.setAiSourceMessageId(messageId);
        supportConversations.saveAndFlush(thread);
        return status(id);
    }

    private Source source(String conversationId, Long messageId, Long ownerId, boolean lock) {
        var conversation = lock
            ? aiConversations.findAccessibleByIdForUpdate(conversationId, ownerId, null)
            : aiConversations.findAccessibleById(conversationId, ownerId, null);
        conversation.orElseThrow(this::missing);
        AiMessage answer = aiMessages.findByIdAndConversationId(messageId, conversationId).orElseThrow(this::missing);
        if (!"model".equals(answer.getRole()) || !answer.isSupportOffered()) throw missing();
        AiMessage question = aiMessages.findFirstByConversationIdAndIdLessThanOrderByIdDesc(conversationId, messageId)
            .filter(m -> "user".equals(m.getRole())).orElseThrow(this::missing);
        List<AiMessage> context = new ArrayList<>(aiMessages.findByConversationIdAndIdLessThanOrderByIdDesc(
            conversationId, question.getId(), PageRequest.of(0, 6)));
        Collections.reverse(context);
        return new Source(question, List.copyOf(context));
    }

    private RequestStatus status(Long id) {
        var thread = support.thread(id, false, 0);
        var lastReply = thread.messages().stream().filter(message -> message.adminMessage()).reduce((a, b) -> b);
        return new RequestStatus(id, thread.conversation().status().name(), thread.conversation().status().getLabel(),
            "/support/" + id, lastReply.map(message -> message.content()).orElse(null),
            lastReply.map(message -> message.sentAt()).orElse(null));
    }

    private Long ownerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser().getId() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để liên hệ nhân viên hỗ trợ.");
        return users.findById(details.getUser().getId()).map(user -> user.getId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn khả dụng."));
    }
    private SharedMessage shared(AiMessage m) { return new SharedMessage(m.getId(), m.getRole(), m.getText(), m.getCreatedAt()); }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu chuyển hỗ trợ hoặc bạn không có quyền truy cập.");
    }
    private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
