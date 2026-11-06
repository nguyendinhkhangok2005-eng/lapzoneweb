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
    public String showAppointmentPage(HttpSession session, Model model) {
        AppointmentForm form = new AppointmentForm();
        Object currentUser = session.getAttribute("currentUser");
        if (currentUser instanceof User user) {
            form.setFullName(user.getFullName());
            form.setEmail(user.getEmail());
            form.setPhone(user.getPhone());
        }
        model.addAttribute("appointmentForm", form);
        return "datlich";
    }

    @PostMapping("/datlich")
    public String submitAppointment(@Valid @ModelAttribute("appointmentForm") AppointmentForm form,
                                    BindingResult bindingResult,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "datlich";
        }

        try {
            appointmentService.createAppointment(form);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "datlich";
        }

        redirectAttributes.addFlashAttribute(
                "appointmentSuccess", "Yêu cầu đặt lịch đã được gửi. LAPZONE sẽ sớm liên hệ xác nhận với bạn.");
        return "redirect:/datlich";
    }
}