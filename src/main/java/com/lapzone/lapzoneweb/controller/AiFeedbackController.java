package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiFeedbackService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class AiFeedbackController {
    private final AiFeedbackService feedback;
    public AiFeedbackController(AiFeedbackService feedback) { this.feedback = feedback; }

    @GetMapping("/api/ai/feedback/csrf")
    public ResponseEntity<Map<String, String>> token(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @GetMapping("/api/ai/conversations/{conversationId}/feedback")
    public List<AiFeedbackService.Vote> votes(@PathVariable String conversationId, HttpSession session) {
        return feedback.votes(conversationId, AiConsultantController.access(session));
    }
    @PostMapping("/api/ai/conversations/{conversationId}/messages/{messageId}/feedback")
    public AiFeedbackService.Vote submit(@PathVariable String conversationId, @PathVariable Long messageId,
                                        @RequestBody AiFeedbackService.Submission request, HttpSession session) {
        return feedback.submit(conversationId, messageId, AiConsultantController.access(session), request);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", Objects.requireNonNullElse(error.getReason(), "Không thể xử lý đánh giá.")));
    }
}
