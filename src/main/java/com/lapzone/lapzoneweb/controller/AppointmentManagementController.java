package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AppointmentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@PreAuthorize("isAuthenticated()")
public class AppointmentManagementController {
    private final AppointmentService appointments;
    public AppointmentManagementController(AppointmentService appointments) { this.appointments = appointments; }
    public record StatusUpdate(@NotBlank(message = "Vui lòng chọn trạng thái.") String status,
            @Size(max = 2000, message = "Lý do không được vượt quá 2.000 ký tự.") String reason,
            @Size(max = 200, message = "Tên cửa hàng không được vượt quá 200 ký tự.") String showroom) {}
    public record Cancellation(@NotBlank(message = "Vui lòng nhập lý do hủy.")
            @Size(max = 2000, message = "Lý do không được vượt quá 2.000 ký tự.") String reason) {}

    @ModelAttribute
    void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/api/appointments")
    public AppointmentService.AppointmentPage mine(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return appointments.customerAppointments(status, page, size);
    }
    @GetMapping("/api/appointments/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(), "token", token.getToken());
    }
    @GetMapping("/api/appointments/{id}")
    public AppointmentService.Detail mine(@PathVariable Long id) { return appointments.customerDetail(id); }
    @PostMapping("/api/appointments/{id}/cancel")
    public AppointmentService.Detail cancel(@PathVariable Long id, @Valid @RequestBody Cancellation body) {
        return appointments.cancel(id, body.reason());
    }
    @GetMapping("/admin/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentService.AppointmentPage adminList(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return appointments.adminAppointments(status, page, size);
    }
    @GetMapping("/admin/appointments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentService.Detail adminDetail(@PathVariable Long id) { return appointments.adminDetail(id); }
    @PostMapping("/admin/appointments/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentService.Detail change(@PathVariable Long id, @Valid @RequestBody StatusUpdate body) {
        return appointments.changeStatus(id, body.status(), body.reason(), body.showroom());
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Không thể xử lý lịch hẹn." : e.getReason()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message", "Dữ liệu lịch hẹn không hợp lệ.")); }
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<?> busy() { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Lịch hẹn đang được cập nhật. Vui lòng thử lại.")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền truy cập lịch hẹn.")); }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().map(error -> error.getDefaultMessage()).sorted().findFirst().orElse("Thông tin lịch hẹn không hợp lệ.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
