package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.ReturnRequestService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@PreAuthorize("isAuthenticated()")
public class ReturnRequestPageController {
    private final ReturnRequestService service;
    public ReturnRequestPageController(ReturnRequestService service) { this.service = service; }

    @GetMapping("/store/orders/{orderId}/items/{itemId}/return-request")
    public String form(@PathVariable Long orderId, @PathVariable Long itemId, Model model) {
        model.addAttribute("item", service.eligibleItem(orderId, itemId));
        return "orders/return-request";
    }

    @PostMapping("/store/orders/{orderId}/items/{itemId}/return-request")
    public String submit(@PathVariable Long orderId, @PathVariable Long itemId,
                         @RequestParam(required = false) Integer quantity, @RequestParam(defaultValue = "REFUND") String type,
                         @RequestParam(defaultValue = "") String reason, RedirectAttributes redirect) {
        try {
            service.submit(orderId, itemId, quantity, type, reason);
            redirect.addFlashAttribute("success", "Đã gửi yêu cầu. Bạn có thể theo dõi trong mục Trả hàng.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("draftReason", reason.substring(0, Math.min(reason.length(), 2000)));
            redirect.addFlashAttribute("draftQuantity", quantity);
            redirect.addFlashAttribute("draftType", "RETURN".equalsIgnoreCase(type.trim()) ? "RETURN" : "REFUND");
            return "redirect:/store/orders/" + orderId + "/items/" + itemId + "/return-request";
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "Sản phẩm đã có yêu cầu. Vui lòng kiểm tra mục Trả hàng.");
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) throw e;
            redirect.addFlashAttribute("error", e.getReason());
        }
        return "redirect:/store/orders/" + orderId + "?category=RETURNS";
    }

    @GetMapping("/admin/returns")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String status, Model model) {
        model.addAttribute("requests", service.adminList(page, status));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", List.of("REQUESTED", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED"));
        return "admin/returns";
    }

    @PostMapping("/admin/returns/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public String decide(@PathVariable Long id, @RequestParam String status, @RequestParam(defaultValue = "") String note,
                         @RequestParam(defaultValue = "false") boolean manualProcessingConfirmed, RedirectAttributes redirect) {
        try {
            service.decide(id, status, note, manualProcessingConfirmed);
            redirect.addFlashAttribute("success", "Đã cập nhật trạng thái yêu cầu.");
        } catch (IllegalArgumentException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/returns";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String missing(ResponseStatusException e, HttpServletResponse response, Model model) {
        response.setStatus(e.getStatusCode().value());
        model.addAttribute("message", e.getReason()); return "orders/error";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException e, HttpServletResponse response, Model model) {
        response.setStatus(400); model.addAttribute("message", e.getMessage()); return "orders/error";
    }
    @ExceptionHandler(AccessDeniedException.class)
    public String denied(HttpServletResponse response, Model model) {
        response.setStatus(403); model.addAttribute("message", "Bạn không có quyền xử lý yêu cầu này."); return "orders/error";
    }
}
