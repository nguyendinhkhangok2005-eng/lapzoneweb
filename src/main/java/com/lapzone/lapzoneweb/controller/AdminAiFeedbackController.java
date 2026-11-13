package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiFeedbackService;
import com.lapzone.lapzoneweb.model.entity.AiFeedback;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/admin/ai/feedback")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiFeedbackController {
    private final AiFeedbackService feedback;
    public AdminAiFeedbackController(AiFeedbackService feedback) { this.feedback = feedback; }
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String rating, @RequestParam(defaultValue = "") String reason,
                       @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String through,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        try {
            model.addAttribute("data", feedback.reports(rating, reason, from.isBlank() ? today.minusDays(29) : LocalDate.parse(from),
                through.isBlank() ? today : LocalDate.parse(through), page));
        } catch (DateTimeParseException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày lọc không hợp lệ."); }
        model.addAttribute("reasons", AiFeedback.Reason.values());
        return "admin/ai-feedback";
    }
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id) {
        feedback.review(id);
        return "redirect:/admin/ai/feedback";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
            .body(error.getReason());
    }
}
