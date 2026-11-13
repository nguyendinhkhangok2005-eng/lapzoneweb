package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.AiResponseSettings;
import com.lapzone.lapzoneweb.model.service.AiResponseSettingsService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/ai/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiSettingsController {
    private final AiResponseSettingsService settings;
    public AdminAiSettingsController(AiResponseSettingsService settings) { this.settings = settings; }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("form", settings.asInput(settings.current()));
        details(model);
        return "admin/ai-settings";
    }
    @PostMapping("/preview")
    public String preview(@ModelAttribute AiResponseSettingsService.Input input, Model model) {
        model.addAttribute("preview", settings.preview(input));
        model.addAttribute("form", input);
        details(model);
        return "admin/ai-settings";
    }
    @PostMapping
    public String save(@ModelAttribute AiResponseSettingsService.Input input) {
        settings.save(input); return "redirect:/admin/ai/settings";
    }
    @PostMapping("/reset")
    public String reset(@RequestParam(required = false) Long expectedVersion) {
        settings.reset(expectedVersion); return "redirect:/admin/ai/settings";
    }
    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("history", settings.history(page));
        return "admin/ai-settings-history";
    }
    private void details(Model model) {
        model.addAttribute("styles", AiResponseSettings.Style.values());
        model.addAttribute("lengths", AiResponseSettings.Length.values());
        model.addAttribute("history", settings.history(0));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
            .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(error.getReason());
    }
}
