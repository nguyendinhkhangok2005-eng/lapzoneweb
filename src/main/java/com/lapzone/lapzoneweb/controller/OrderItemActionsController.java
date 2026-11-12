package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.OrderItemActionsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("isAuthenticated()")
public class OrderItemActionsController {
    private final OrderItemActionsService actions;

    public OrderItemActionsController(OrderItemActionsService actions) { this.actions = actions; }

    @GetMapping("/store/orders/{orderId}/items/{itemId}/review")
    public String reviewForm(@PathVariable Long orderId, @PathVariable Long itemId, Model model) {
        model.addAttribute("item", actions.eligibleReview(orderId, itemId));
        return "orders/review";
    }

    @PostMapping("/store/orders/{orderId}/items/{itemId}/review")
    public String review(@PathVariable Long orderId, @PathVariable Long itemId,
                         @RequestParam(required = false) Integer rating, @RequestParam(defaultValue = "") String content,
                         RedirectAttributes redirect) {
        try {
            actions.submitReview(orderId, itemId, rating, content);
            redirect.addFlashAttribute("success", "Đã lưu đánh giá của bạn.");
            return "redirect:/store/orders/" + orderId + "?category=REVIEW";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("draftRating", rating);
            redirect.addFlashAttribute("draftContent", content.substring(0, Math.min(content.length(), 4000)));
            return "redirect:/store/orders/" + orderId + "/items/" + itemId + "/review";
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "Sản phẩm trong đơn này đã được đánh giá.");
            return "redirect:/store/orders/" + orderId;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) throw e;
            redirect.addFlashAttribute("error", e.getReason());
            return "redirect:/store/orders/" + orderId;
        }
    }

    @PostMapping("/store/orders/{orderId}/items/{itemId}/repurchase")
    public String repurchase(@PathVariable Long orderId, @PathVariable Long itemId, RedirectAttributes redirect) {
        try {
            actions.repurchase(orderId, itemId);
            redirect.addFlashAttribute("cartSuccess", "Đã thêm sản phẩm vào giỏ hàng theo giá hiện tại.");
            return "redirect:/cart";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) throw e;
            redirect.addFlashAttribute("error", e.getReason());
            return "redirect:/store/orders/" + orderId;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String missing(ResponseStatusException e, HttpServletResponse response, Model model) {
        response.setStatus(e.getStatusCode().value());
        model.addAttribute("message", e.getReason());
        return "orders/error";
    }
}
