package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.Map;

@RestController
@PreAuthorize("isAuthenticated()")
public class CustomerOrderController {
    private final CustomerOrderService service;
    public CustomerOrderController(CustomerOrderService service) { this.service = service; }

    @GetMapping("/api/orders/categories")
    public Map<String, Object> categories() {
        return Map.of("categories", Arrays.stream(CustomerOrderCategory.values()).map(CustomerOrderService::category).toList(),
                "counts", service.categoryCounts(), "categoriesMayOverlap", true,
                "reviewSubmissionSupported", true, "returnSubmissionSupported", true);
    }
    @GetMapping("/api/orders")
    public CustomerOrderService.OrderPage list(@RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(category, page, size);
    }
    @GetMapping("/api/orders/{id}")
    public CustomerOrderService.DetailView detail(@PathVariable Long id) { return service.detail(id); }

    @GetMapping("/api/orders/{orderId}/items/{itemId}")
    public CustomerOrderService.ItemView item(@PathVariable Long orderId, @PathVariable Long itemId) { return service.item(orderId, itemId); }

    @GetMapping("/admin/orders/{id}/data")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerOrderService.DetailView adminDetail(@PathVariable Long id) { return service.adminDetail(id); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> missing(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", "Không tìm thấy đơn hàng hoặc sản phẩm.")); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền truy cập đơn hàng.")); }
}
