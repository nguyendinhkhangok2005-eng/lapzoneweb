package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiConversationService;
import com.lapzone.lapzoneweb.model.service.AiChatRateLimiter;
import com.lapzone.lapzoneweb.model.dto.ConversationDTO;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AiConsultantController {

    private static final String DEFAULT_CONVERSATION_KEY = "lapzoneAiDefaultConversation:";
    private static final String GUEST_KEY = "lapzoneAiGuestKey";
    private static final int MAX_QUESTION_LENGTH = 500;
    private final AiConversationService conversations;
    private final AiChatRateLimiter rateLimiter;

    public AiConsultantController(AiConversationService conversations, AiChatRateLimiter rateLimiter) {
        this.conversations = conversations;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/ai/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequestDTO request,
                                                    HttpSession session,
                                                    HttpServletRequest servletRequest) {
        String question = cleanControls(request == null ? null : request.message()).strip();

        if (question.isBlank()) {
            return ResponseEntity.ok(Map.of("reply", "Xin chào! Hãy mô tả nhu cầu của bạn, ví dụ: laptop gaming dưới 20 triệu hoặc máy đồ họa cho thiết kế."));
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                "reply", "Câu hỏi tối đa 500 ký tự. Vui lòng rút gọn rồi gửi lại."
            ));
        }

        long retryAfterSeconds = rateLimiter.retryAfterSeconds(servletRequest.getRemoteAddr());
        if (retryAfterSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "reply", "Bạn gửi yêu cầu hơi nhanh. Vui lòng thử lại sau "
                    + retryAfterSeconds + " giây nhé."
            ));
        }

        AiConversationService.Access access = access(session);
        String id = request == null ? null : request.conversationId();
        if (id == null) {
            synchronized (session) {
                String key = defaultKey(access);
                id = (String) session.getAttribute(key);
                if (id == null) {
                    id = conversations.create(access, null).id();
                    session.setAttribute(key, id);
                }
            }
        }
        String reply = conversations.chat(id, access, question);
        return ResponseEntity.ok(Map.of("reply", reply, "conversationId", id));
    }

    @PostMapping("/api/chat/reset")
    public ResponseEntity<Map<String, String>> reset(HttpSession session,
            @RequestParam(required = false) String conversationId) {
        AiConversationService.Access access = access(session);
        if (conversationId != null) {
            conversations.reset(conversationId, access);
        } else {
            // The legacy UI starts fresh while its previous conversation remains in history.
            synchronized (session) {
                session.removeAttribute(defaultKey(access));
                session.removeAttribute("lapzoneAiChatHistory");
            }
        }
        return ResponseEntity.ok(Map.of("reply", "Cuộc trò chuyện đã được làm mới."));
    }

    @PostMapping("/api/ai/conversations")
    public ResponseEntity<ConversationDTO> create(@RequestBody(required = false) ConversationDTO.TitleRequest request,
                                                  HttpSession session) {
        ConversationDTO created = conversations.create(access(session), request == null ? null : request.title());
        return ResponseEntity.created(java.net.URI.create("/api/ai/conversations/" + created.id())).body(created);
    }

    @GetMapping("/api/ai/conversations")
    public List<ConversationDTO> list(HttpSession session, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return conversations.list(access(session), page, size);
    }

    @GetMapping("/api/ai/conversations/{id}")
    public ConversationDTO.Detail retrieve(@PathVariable String id, HttpSession session) {
        return conversations.retrieve(id, access(session));
    }

    @PatchMapping("/api/ai/conversations/{id}")
    public ConversationDTO rename(@PathVariable String id, @RequestBody ConversationDTO.TitleRequest request,
                                   HttpSession session) {
        return conversations.rename(id, access(session), request == null ? null : request.title());
    }

    @DeleteMapping("/api/ai/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpSession session) {
        AiConversationService.Access access = access(session);
        conversations.delete(id, access);
        synchronized (session) {
            if (id.equals(session.getAttribute(defaultKey(access)))) {
                session.removeAttribute(defaultKey(access));
            }
        }
        return ResponseEntity.noContent().build();
    }

    private AiConversationService.Access access(HttpSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getPrincipal() instanceof CustomUserDetails details
                    && details.getUser().getId() != null) {
                return new AiConversationService.Access(details.getUser().getId(), null);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        synchronized (session) {
            String key = (String) session.getAttribute(GUEST_KEY);
            if (key == null) {
                key = UUID.randomUUID().toString();
                session.setAttribute(GUEST_KEY, key);
            }
            return new AiConversationService.Access(null, key);
        }
    }

    private String defaultKey(AiConversationService.Access access) {
        return DEFAULT_CONVERSATION_KEY + (access.ownerId() == null ? "guest" : access.ownerId());
    }

    private String cleanControls(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!Character.isISOControl(character)) {
                cleaned.append(character);
            }
        }
        return cleaned.toString();
    }
}
