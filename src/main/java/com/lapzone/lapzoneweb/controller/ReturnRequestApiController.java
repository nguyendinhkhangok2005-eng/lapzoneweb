package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.ReturnRequestService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/orders")
public class ReturnRequestApiController {
    private final ReturnRequestService service;
    public ReturnRequestApiController(ReturnRequestService service) { this.service = service; }
    public record Submission(Integer quantity, String type, String reason) {}

    @PostMapping("/{orderId}/items/{itemId}/return-requests")
    public ResponseEntity<ReturnRequestService.RequestView> submit(@PathVariable Long orderId, @PathVariable Long itemId,
                                                                  @RequestBody Submission form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(orderId, itemId, form.quantity(), form.type(), form.reason()));
    }
    @GetMapping("/return-requests/{id}/history")
    public List<ReturnRequestService.EventView> history(@PathVariable Long id) { return service.history(id); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> rejected(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason())); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate() { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Sản phẩm này đã có yêu cầu. Vui lòng kiểm tra mục Trả hàng.")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền xử lý yêu cầu này.")); }
}
