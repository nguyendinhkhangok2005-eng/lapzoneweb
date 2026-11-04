package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import com.lapzone.lapzoneweb.model.service.AiChatRateLimiter;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class AiConsultantController {

    private static final String CHAT_HISTORY_SESSION_KEY = "lapzoneAiChatHistory";
    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_HISTORY_MESSAGES = 12;
    private final AiConsultantService aiConsultantService;
    private final AiChatRateLimiter rateLimiter;

    @Autowired
    public AiConsultantController(AiConsultantService aiConsultantService, AiChatRateLimiter rateLimiter) {
        this.aiConsultantService = aiConsultantService;
        this.rateLimiter = rateLimiter;
    }

    public AiConsultantController(AiConsultantService aiConsultantService) {
        this(aiConsultantService, new AiChatRateLimiter(6, 60, 30, java.time.Clock.systemUTC()));
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

        List<ChatMessageDTO> history = getHistory(session);
        String reply = aiConsultantService.consult(question, List.copyOf(history));
        history.add(new ChatMessageDTO("user", question));
        history.add(new ChatMessageDTO("model", reply));
        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }
        session.setAttribute(CHAT_HISTORY_SESSION_KEY, history);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    @PostMapping("/api/chat/reset")
    public ResponseEntity<Map<String, String>> reset(HttpSession session) {
        session.removeAttribute(CHAT_HISTORY_SESSION_KEY);
        return ResponseEntity.ok(Map.of("reply", "Cuộc trò chuyện đã được làm mới."));
    }

    private List<ChatMessageDTO> getHistory(HttpSession session) {
        Object value = session.getAttribute(CHAT_HISTORY_SESSION_KEY);
        if (value instanceof List<?> messages) {
            List<ChatMessageDTO> history = messages.stream()
                .filter(ChatMessageDTO.class::isInstance)
                .map(ChatMessageDTO.class::cast)
                .filter(message -> "user".equals(message.role()) || "model".equals(message.role()))
                .map(message -> new ChatMessageDTO(message.role(), cleanControls(message.text())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (history.size() > MAX_HISTORY_MESSAGES) {
                return new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
            }
            return history;
        }
        return new ArrayList<>();
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
