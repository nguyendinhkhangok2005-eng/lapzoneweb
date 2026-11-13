package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Controller
@RequestMapping("/admin/ai/inspector")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiInspectionController {
    private final AdminAiInspectionService inspector;
    private final AiInspectionRunStore store;
    public AdminAiInspectionController(AdminAiInspectionService inspector, AiInspectionRunStore store) { this.inspector = inspector; this.store = store; }
    @GetMapping
    public String page(@RequestParam(defaultValue = "GAMING") String preset, @RequestParam(defaultValue = "0") int page, Model model) {
        AdminAiInspectionService.Preset selected;
        try { selected = AdminAiInspectionService.Preset.valueOf(preset); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi mẫu không hợp lệ."); }
        model.addAttribute("runs", store.list(page)); model.addAttribute("presets", AdminAiInspectionService.Preset.values());
        model.addAttribute("selected", selected); model.addAttribute("requestToken", UUID.randomUUID().toString());
        return "admin/ai-inspector";
    }
    @PostMapping("/run")
    public String run(@RequestParam String question, @RequestParam String requestToken, @RequestParam(defaultValue = "CUSTOM") String preset) {
        return "redirect:/admin/ai/inspector/" + inspector.run(question, preset, requestToken);
    }
    @GetMapping("/{id}")
    public String result(@PathVariable Long id, Model model) {
        model.addAttribute("data", store.get(id)); return "admin/ai-inspector-result";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(error.getReason());
    }
}
