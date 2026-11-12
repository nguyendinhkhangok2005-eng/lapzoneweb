package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SupportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final UserRepository users;

    public SupportService(SupportConversationRepository conversations, SupportMessageRepository messages, UserRepository users) {
        this.conversations = conversations; this.messages = messages; this.users = users;
    }

    public record ConversationView(Long id, String subject, String customerName, SupportConversation.Status status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, long unread) {}
    public record MessageView(Long id, String content, boolean adminMessage, LocalDateTime sentAt) {}
    public record ThreadView(ConversationView conversation, List<MessageView> messages, int page,
                             boolean older, boolean newer, Long throughId) {}

    public Page<ConversationView> inbox(boolean admin, int page) {
        User actor = actor(admin);
        var result = conversations.inbox(actor.getId(), admin, PageRequest.of(safePage(page), 20,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        Map<Long, Long> unread = result.isEmpty() ? Map.of() : messages.unreadByConversation(
                result.map(c -> c.getId()).getContent(), !admin).stream()
                .collect(Collectors.toMap(c -> c.getConversationId(), c -> c.getUnreadCount()));
        return result.map(c -> view(c, unread.getOrDefault(c.getId(), 0L)));
    }

    public long unreadCount(boolean admin) {
        User actor = actor(admin);
        return messages.unreadTotal(actor.getId(), admin, !admin);
    }

    public ThreadView thread(Long id, boolean admin, int page) {
        User actor = actor(admin);
        SupportConversation conversation = accessible(id, actor, admin, false);
        var history = messages.findByConversationIdOrderByIdDesc(id, PageRequest.of(safePage(page), 50));
        List<MessageView> content = new ArrayList<>(history.map(m -> new MessageView(
                m.getId(), m.getContent(), m.isAdminMessage(), m.getSentAt())).getContent());
        Collections.reverse(content);
        long unread = messages.countByConversationIdAndAdminMessageAndReadAtIsNull(id, !admin);
        return new ThreadView(view(conversation, unread), List.copyOf(content), history.getNumber(),
                history.hasNext(), history.hasPrevious(), content.isEmpty() ? null : content.getLast().id());
    }

    @Transactional
    public Long create(String subject, String content) {
        User actor = actor(false);
        String title = validated(subject, 120, "Tiêu đề");
        String body = validated(content, 4000, "Tin nhắn");
        SupportConversation conversation = new SupportConversation();
        conversation.setCustomer(actor); conversation.setSubject(title);
        conversation.setCreatedAt(now()); conversation.setUpdatedAt(conversation.getCreatedAt());
        conversations.save(conversation);
        append(conversation, actor, body, false);
        return conversation.getId();
    }

    @Transactional
    public void send(Long id, String content, boolean admin) {
        User actor = actor(admin);
        SupportConversation conversation = accessible(id, actor, admin, true);
        if (conversation.getStatus() == SupportConversation.Status.CLOSED)
            throw new IllegalArgumentException("Hội thoại đã đóng. Vui lòng tạo yêu cầu mới hoặc chờ quản trị viên mở lại.");
        append(conversation, actor, validated(content, 4000, "Tin nhắn"), admin);
        conversation.setStatus(admin ? SupportConversation.Status.REPLIED : SupportConversation.Status.WAITING);
        conversation.setUpdatedAt(now());
    }

    @Transactional
    public void markRead(Long id, Long throughId, boolean admin) {
        User actor = actor(admin);
        accessible(id, actor, admin, true);
        if (throughId == null || throughId < 1) throw new IllegalArgumentException("Mốc tin nhắn không hợp lệ.");
        // Only mark messages already delivered to this page, never a concurrent later reply.
        messages.markRead(id, throughId, !admin, now());
    }

    @Transactional
    public void setClosed(Long id, boolean closed) {
        User actor = actor(true);
        SupportConversation conversation = accessible(id, actor, true, true);
        conversation.setStatus(closed ? SupportConversation.Status.CLOSED : SupportConversation.Status.WAITING);
        conversation.setUpdatedAt(now());
    }

    private void append(SupportConversation conversation, User actor, String content, boolean admin) {
        SupportMessage message = new SupportMessage();
        message.setConversation(conversation); message.setSender(actor); message.setAdminMessage(admin);
        message.setContent(content); message.setSentAt(now()); messages.save(message);
    }

    private SupportConversation accessible(Long id, User actor, boolean admin, boolean lock) {
        return (lock ? conversations.lockAccessible(id, actor.getId(), admin) : conversations.accessible(id, actor.getId(), admin))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại."));
    }

    private User actor(boolean admin) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser().getId() == null) throw new AccessDeniedException("Vui lòng đăng nhập để nhắn tin hỗ trợ.");
        User user = users.findById(details.getUser().getId())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn khả dụng."));
        if (admin && (!"ADMIN".equals(user.getRole()) || auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))))
            throw new AccessDeniedException("Bạn không có quyền quản trị tin nhắn.");
        return user;
    }

    private ConversationView view(SupportConversation c, long unread) {
        String name = c.getCustomer().getFullName();
        return new ConversationView(c.getId(), c.getSubject(), name == null || name.isBlank() ? "Khách hàng" : name,
                c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(), unread);
    }
    private String validated(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new IllegalArgumentException(label + " phải có từ 1 đến " + max + " ký tự.");
        String clean = value.strip();
        if (clean.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException(label + " chứa ký tự không hợp lệ.");
        return clean;
    }
    private int safePage(int page) {
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Trang không hợp lệ.");
        return page;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
}
