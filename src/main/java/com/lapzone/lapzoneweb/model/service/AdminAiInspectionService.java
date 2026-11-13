package com.lapzone.lapzoneweb.model.service;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class AdminAiInspectionService {
    public enum Preset {
        GAMING("Chơi game", "Tìm laptop gaming dưới 25 triệu, RAM 16GB và GPU RTX 4050."),
        OFFICE("Văn phòng", "Tìm laptop văn phòng dưới 18 triệu, RAM 16GB và SSD 512GB."),
        PROGRAMMING("Lập trình", "Tìm laptop lập trình dưới 35 triệu, CPU i7, RAM 32GB và SSD 1TB."),
        GRAPHICS("Đồ họa", "Tìm laptop đồ họa dưới 40 triệu, GPU RTX 4060 và RAM 32GB."),
        BUDGET("Theo ngân sách", "Tìm laptop từ 12 đến 15 triệu, RAM 16GB và SSD 512GB.");
        private final String label, question;
        Preset(String label, String question) { this.label = label; this.question = question; }
        public String getLabel() { return label; }
        public String getQuestion() { return question; }
    }
    private final AiConsultantService consultant;
    private final AiRecommendationValidator validator;
    private final AiInspectionRunStore store;
    public AdminAiInspectionService(AiConsultantService consultant, AiRecommendationValidator validator, AiInspectionRunStore store) {
        this.consultant = consultant; this.validator = validator; this.store = store;
    }
    // There is deliberately no scheduled or batch execution path.
    public Long run(String rawQuestion, String presetText, String token) {
        String question = Objects.toString(rawQuestion, "").replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
        if (question.isBlank() || question.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi cần có từ 1 đến 500 ký tự.");
        try { if (token == null || !UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã yêu cầu không hợp lệ. Vui lòng mở lại trang kiểm tra."); }
        String preset = Arrays.stream(Preset.values()).filter(p -> p.name().equals(presetText) && p.getQuestion().equals(question))
            .map(p -> p.name()).findFirst().orElse("CUSTOM");
        var reservation = store.reserve(question, preset, token);
        if (!reservation.execute()) return reservation.id();
        try {
            var response = consultant.consultWithProducts(question, List.of());
            store.complete(reservation.id(), validator.validate(question, response));
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("AI inspection {} did not complete ({})", reservation.id(), ex.getClass().getSimpleName());
            store.fail(reservation.id());
        }
        return reservation.id();
    }
}
