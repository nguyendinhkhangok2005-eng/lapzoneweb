package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("isAuthenticated()")
public class SupportController {
    private final SupportService support;
    public SupportController(SupportService support) { this.support = support; }

    @GetMapping({"/support", "/admin/support"})
    public String inbox(@RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        boolean admin = admin(request);
        model.addAttribute("conversations", support.inbox(admin, page));
        model.addAttribute("unreadCount", support.unreadCount(admin));
        layout(model, admin);
        return "support/inbox";
    }

    @GetMapping({"/support/{id}", "/admin/support/{id}"})
    public String thread(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                         HttpServletRequest request, Model model) {
        boolean admin = admin(request);
        model.addAttribute("thread", support.thread(id, admin, page));
        layout(model, admin);
        return "support/thread";
    }

    @PostMapping("/support")
    public String create(@RequestParam(defaultValue = "") String subject, @RequestParam(defaultValue = "") String content,
                         RedirectAttributes redirect) {
        try {
            return "redirect:/support/" + support.create(subject, content);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("draftSubject", draft(subject, 120));
            redirect.addFlashAttribute("draftContent", draft(content, 4000));
            return "redirect:/support";
        }
    }

    @PostMapping({"/support/{id}/messages", "/admin/support/{id}/messages"})
    public String send(@PathVariable Long id, @RequestParam(defaultValue = "") String content,
                       HttpServletRequest request, RedirectAttributes redirect) {
        try {
            support.send(id, content, admin(request));
            redirect.addFlashAttribute("success", "Đã gửi tin nhắn.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("draftContent", draft(content, 4000));
        }
        return "redirect:" + base(admin(request)) + "/" + id;
    }

    @PostMapping({"/support/{id}/read", "/admin/support/{id}/read"})
    public String read(@PathVariable Long id, @RequestParam Long throughId, @RequestParam(defaultValue = "0") int page,
                       HttpServletRequest request, HttpServletResponse response) {
        support.markRead(id, throughId, admin(request));
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return null;
        }
        return "redirect:" + base(admin(request)) + "/" + id + "?page=" + Math.max(0, page);
    }

    @PostMapping("/admin/support/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public String status(@PathVariable Long id, @RequestParam boolean closed, RedirectAttributes redirect) {
        support.setClosed(id, closed);
        redirect.addFlashAttribute("success", closed ? "Đã đóng hội thoại." : "Đã mở lại hội thoại.");
        return "redirect:/admin/support/" + id;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String missing(ResponseStatusException error, HttpServletResponse response, Model model) {
        response.setStatus(error.getStatusCode().value());
        model.addAttribute("message", "Không tìm thấy hội thoại hoặc bạn không có quyền truy cập.");
        return "support/error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String denied(HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("message", "Bạn không có quyền truy cập tin nhắn này.");
        return "support/error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException error, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("message", error.getMessage());
        return "support/error";
    }

    private void layout(Model model, boolean admin) {
        model.addAttribute("adminView", admin); model.addAttribute("supportBase", base(admin));
    }
    private boolean admin(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/");
    }
    private String base(boolean admin) { return admin ? "/admin/support" : "/support"; }
    private String draft(String value, int max) { return value.substring(0, Math.min(value.length(), max)); }
}
