package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.AppointmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/datlich")
    public String showAppointmentPage(HttpSession session,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "booking") String tab, Model model) {
        AppointmentForm form = new AppointmentForm();
        Object currentUser = session.getAttribute("currentUser");
        if (currentUser instanceof User user) {
            form.setFullName(user.getFullName());
            form.setEmail(user.getEmail());
            form.setPhone(user.getPhone());
        }
        model.addAttribute("appointmentForm", form);
        model.addAttribute("appointmentTab", "history".equals(tab) ? "history" : "booking");
        return "datlich";
    }

    @PostMapping("/datlich")
    public String submitAppointment(@Valid @ModelAttribute("appointmentForm") AppointmentForm form,
                                    BindingResult bindingResult,
                                    Model model,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    RedirectAttributes redirectAttributes) {
        model.addAttribute("appointmentTab", "booking");
        if (bindingResult.hasErrors()) {
            return "datlich";
        }

        try {
            appointmentService.createAppointment(form);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "datlich";
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            response.setStatus(exception.getStatusCode().value());
            model.addAttribute("error", exception.getReason());
            return "datlich";
        } catch (org.springframework.dao.PessimisticLockingFailureException exception) {
            response.setStatus(409);
            model.addAttribute("error", "Lịch hẹn đang được cập nhật. Vui lòng thử lại.");
            return "datlich";
        }

        redirectAttributes.addFlashAttribute(
                "appointmentSuccess", "Yêu cầu đặt lịch đã được gửi. LAPZONE sẽ sớm liên hệ xác nhận với bạn.");
        return "redirect:/datlich";
    }

    @PostMapping("/admin/support/appointments/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public String adminStatus(@org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam String status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String reason,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String showroom,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String filterStatus,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long appointmentId,
            RedirectAttributes redirect) {
        try {
            var result = appointmentService.changeStatus(id, status, reason, showroom);
            String label = com.lapzone.lapzoneweb.model.entity.AppointmentStatus.labelFor(result.appointment().status());
            redirect.addFlashAttribute("appointmentActionSuccess", "Lịch hẹn #" + id + ": " + label + ".");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            redirect.addFlashAttribute("error", exception.getReason());
        } catch (org.springframework.dao.PessimisticLockingFailureException exception) {
            redirect.addFlashAttribute("error", "Lịch hẹn đang được cập nhật. Vui lòng thử lại.");
        }
        var destination = org.springframework.web.util.UriComponentsBuilder.fromPath("/admin/support")
                .queryParam("tab", "appointments");
        if (java.util.Arrays.stream(com.lapzone.lapzoneweb.model.entity.AppointmentStatus.values())
                .anyMatch(value -> value.name().equals(filterStatus))) destination.queryParam("status", filterStatus);
        if (page > 0 && page <= 100000) destination.queryParam("page", page);
        if (id.equals(appointmentId)) destination.queryParam("appointmentId", id);
        return "redirect:" + destination.build().toUriString();
    }
}
