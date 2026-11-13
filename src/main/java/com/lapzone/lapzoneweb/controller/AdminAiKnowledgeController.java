package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.AiKnowledgeRevision.Category;
import com.lapzone.lapzoneweb.model.service.AiKnowledgeService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/ai/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiKnowledgeController {
    private final AiKnowledgeService knowledge;
    public AdminAiKnowledgeController(AiKnowledgeService knowledge) { this.knowledge = knowledge; }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entries", knowledge.list(page));
        return "admin/ai-knowledge-list";
    }
    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("entry", null); model.addAttribute("categories", Category.values());
        return "admin/ai-knowledge-edit";
    }
    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("entry", knowledge.get(id)); model.addAttribute("categories", Category.values());
        return "admin/ai-knowledge-edit";
    }
    @PostMapping
    public String create(@RequestParam String title, @RequestParam String content,
                         @RequestParam(defaultValue = "") String keywords, @RequestParam String category) {
        return "redirect:/admin/ai/knowledge/" + knowledge.create(new AiKnowledgeService.Draft(title, content, keywords, category));
    }
    @PostMapping("/{id}")
    public String save(@PathVariable Long id, @RequestParam int expectedVersion, @RequestParam String title,
                       @RequestParam String content, @RequestParam(defaultValue = "") String keywords, @RequestParam String category) {
        knowledge.edit(id, expectedVersion, new AiKnowledgeService.Draft(title, content, keywords, category));
        return "redirect:/admin/ai/knowledge/" + id;
    }
    @PostMapping("/{id}/state")
    public String transition(@PathVariable Long id, @RequestParam int expectedVersion, @RequestParam String action,
                             @RequestParam(defaultValue = "false") boolean verified) {
        knowledge.transition(id, expectedVersion, action, verified);
        return "redirect:/admin/ai/knowledge/" + id;
    }
    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entry", knowledge.get(id)); model.addAttribute("versions", knowledge.history(id, page));
        return "admin/ai-knowledge-history";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(error.getReason());
    }
}
