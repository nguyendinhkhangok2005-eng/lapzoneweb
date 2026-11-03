package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.dto.UserRegisterDTO;
import com.lapzone.lapzoneweb.model.service.AuthService;
import com.lapzone.lapzoneweb.model.service.OtpService;
import com.lapzone.lapzoneweb.model.service.OtpService.OtpException;
import com.lapzone.lapzoneweb.model.service.OtpService.Purpose;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;
import java.util.Optional;

@Controller
public class AuthController {

    private static final String PENDING_REGISTRATION_ATTRIBUTE = "pendingRegistration";
    private static final String OTP_ERROR = "Mã xác nhận không chính xác hoặc đã hết hạn. Vui lòng thử lại.";

    private final AuthService authService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthService authService, OtpService otpService, PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/dangnhap")
    public String loginPage() {
        return "dangnhap";
    }

    @GetMapping("/dangki")
    public String registerPage(Model model) {
        model.addAttribute("registerDTO", new UserRegisterDTO());
        return "dangki";
    }

    @PostMapping("/dangki")
    public String handleRegister(@Valid @ModelAttribute("registerDTO") UserRegisterDTO registerDTO,
                                 BindingResult result,
                                 HttpSession session,
                                 Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", "Vui lòng kiểm tra lại các thông tin đăng ký.");
            return "dangki";
        }

        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
            return "dangki";
        }

        String email = OtpService.normalizeEmail(registerDTO.getEmail());
        registerDTO.setEmail(email);
        if (!authService.isRegistrationAvailable(email, registerDTO.getPhone())) {
            model.addAttribute("error", "Email hoặc số điện thoại đã tồn tại!");
            return "dangki";
        }

        otpService.clearOtp(session, Purpose.REGISTER);
        PendingRegistration pending = new PendingRegistration(
                registerDTO.getFullName(), email, registerDTO.getPhone(), registerDTO.getAddress(),
                passwordEncoder.encode(registerDTO.getPassword()));
        try {
            otpService.sendOtp(session, Purpose.REGISTER, email);
        } catch (OtpException ex) {
            session.removeAttribute(PENDING_REGISTRATION_ATTRIBUTE);
            model.addAttribute("error", ex.getMessage());
            return "dangki";
        }

        session.setAttribute(PENDING_REGISTRATION_ATTRIBUTE, pending);
        return registrationOtpPage(session, model, null);
    }

    @GetMapping("/dangki/xac-thuc")
    public String showRegistrationOtp(HttpSession session, Model model) {
        if (!(session.getAttribute(PENDING_REGISTRATION_ATTRIBUTE) instanceof PendingRegistration)) {
            return "redirect:/dangki";
        }
        return registrationOtpPage(session, model, null);
    }

    @PostMapping("/dangki/xac-thuc")
    public String verifyRegistrationOtp(@RequestParam(value = "otp", defaultValue = "") String code,
                                        HttpSession session,
                                        Model model) {
        Object pendingValue = session.getAttribute(PENDING_REGISTRATION_ATTRIBUTE);
        if (!(pendingValue instanceof PendingRegistration pending)) {
            return "redirect:/dangki";
        }

        Optional<String> verifiedEmail = otpService.verifyOtp(session, Purpose.REGISTER, code);
        if (verifiedEmail.isEmpty() || !pending.email().equals(verifiedEmail.get())) {
            return registrationOtpPage(session, model, OTP_ERROR);
        }

        if (!authService.registerEncoded(pending.fullName(), pending.email(), pending.phone(),
                pending.address(), pending.encodedPassword())) {
            session.removeAttribute(PENDING_REGISTRATION_ATTRIBUTE);
            otpService.clearOtp(session, Purpose.REGISTER);
            model.addAttribute("registerDTO", new UserRegisterDTO());
            model.addAttribute("error", "Email hoặc số điện thoại đã được đăng ký. Vui lòng thử lại.");
            return "dangki";
        }

        session.removeAttribute(PENDING_REGISTRATION_ATTRIBUTE);
        model.addAttribute("success", "Đăng ký thành công! Hãy đăng nhập.");
        return "dangnhap";
    }

    @PostMapping("/dangki/xac-thuc/gui-lai")
    public String resendRegistrationOtp(HttpSession session, Model model) {
        Object pendingValue = session.getAttribute(PENDING_REGISTRATION_ATTRIBUTE);
        if (!(pendingValue instanceof PendingRegistration pending)) {
            return "redirect:/dangki";
        }

        String error = null;
        try {
            otpService.sendOtp(session, Purpose.REGISTER, pending.email());
        } catch (OtpException ex) {
            error = ex.getMessage();
        }
        return registrationOtpPage(session, model, error);
    }

    private String registrationOtpPage(HttpSession session, Model model, String error) {
        PendingRegistration pending = (PendingRegistration) session.getAttribute(PENDING_REGISTRATION_ATTRIBUTE);
        if (pending == null) {
            return "redirect:/dangki";
        }
        model.addAttribute("email", pending.email());
        model.addAttribute("resendWaitSeconds", otpService.secondsUntilResend(pending.email()));
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "verify_registration_otp";
    }

    private record PendingRegistration(String fullName, String email, String phone, String address,
                                       String encodedPassword) implements Serializable {
    }
}
