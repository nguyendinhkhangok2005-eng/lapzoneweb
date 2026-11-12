package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.CustomerOrderCategory;
import com.lapzone.lapzoneweb.model.service.CustomerOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Controller
@PreAuthorize("isAuthenticated()")
public class CustomerOrdersPageController {
    private final CustomerOrderService orders;
    public CustomerOrdersPageController(CustomerOrderService orders) { this.orders = orders; }

    @GetMapping("/store/orders")
    public String list(@RequestParam(defaultValue = "ALL") String category,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        var result = orders.list(category, page, 20);
        model.addAttribute("orderPage", result);
        model.addAttribute("activeCategory", result.category().key());
        model.addAttribute("orderCategories", Arrays.stream(CustomerOrderCategory.values())
                .map(CustomerOrderService::category).toList());
        return "orders/list";
    }

    @GetMapping("/store/orders/{id}")
    public String detail(@PathVariable Long id, @RequestParam(defaultValue = "ALL") String category, Model model) {
        var group = CustomerOrderCategory.parse(category);
        model.addAttribute("detail", orders.detail(id));
        model.addAttribute("activeCategory", group.name());
        return "orders/detail";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String missing(ResponseStatusException error, HttpServletResponse response, Model model) {
        response.setStatus(error.getStatusCode().value());
        model.addAttribute("message", "Không tìm thấy đơn hàng hoặc bạn không có quyền xem đơn này.");
        return "orders/error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException error, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("message", error.getMessage());
        return "orders/error";
    }
}
