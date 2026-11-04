package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import jakarta.servlet.http.HttpSession;
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
    private static final int MAX_HISTORY_MESSAGES = 8;
    private final AiConsultantService aiConsultantService;

    public AiConsultantController(AiConsultantService aiConsultantService) {
        this.aiConsultantService = aiConsultantService;
    }

    @PostMapping("/api/ai/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequestDTO request, HttpSession session) {
        String question = request == null ? "" : request.message();

        if (question == null || question.isBlank()) {
            return ResponseEntity.ok(Map.of("reply", "Xin chào! Hãy mô tả nhu cầu của bạn, ví dụ: laptop gaming dưới 20 triệu hoặc máy đồ họa cho thiết kế."));
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
            return messages.stream()
                .filter(ChatMessageDTO.class::isInstance)
                .map(ChatMessageDTO.class::cast)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }
}
