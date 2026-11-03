package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AiConsultantController {

    private final AiConsultantService aiConsultantService;

    public AiConsultantController(AiConsultantService aiConsultantService) {
        this.aiConsultantService = aiConsultantService;
    }

    @PostMapping("/api/ai/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> payload) {
        String question = payload != null ? payload.getOrDefault("message", "") : "";

        if (question == null || question.isBlank()) {
            return ResponseEntity.ok(Map.of("reply", "Xin chào! Hãy mô tả nhu cầu của bạn, ví dụ: laptop gaming dưới 20 triệu hoặc máy đồ họa cho thiết kế."));
        }

        String reply = aiConsultantService.consult(question);
        return ResponseEntity.ok(Map.of("reply", reply));
    }
}
