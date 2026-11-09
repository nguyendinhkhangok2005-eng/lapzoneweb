package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.model.service.OtpService;
import com.lapzone.lapzoneweb.model.service.OtpService.OtpException;
import com.lapzone.lapzoneweb.model.service.OtpService.Purpose;
import com.lapzone.lapzoneweb.model.validation.PasswordPolicy;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Controller
public class ForgotPasswordController {

    static final String RESET_TOKEN_ATTRIBUTE = "passwordResetToken";
    static final String RESET_EMAIL_ATTRIBUTE = "passwordResetEmail";
    static final String RESET_CREATED_AT_ATTRIBUTE = "passwordResetCreatedAt";
    static final String PENDING_RESET_EMAIL_ATTRIBUTE = "pendingPasswordResetEmail";
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);

    private static final String GENERIC_MESSAGE = "Nếu email tồn tại, mã xác nhận đã được gửi.";
    private static final String OTP_ERROR =
            "Mã xác nhận không chính xác hoặc đã hết hạn. Vui lòng kiểm tra và thử lại.";
    private static final String INVALID_RESET_ERROR =
            "Phiên xác minh không hợp lệ hoặc đã hết hạn. Vui lòng xác minh lại.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public ForgotPasswordController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                    OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
    }

    @GetMapping("/forgot-password")
    public String showForgotForm() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam(value = "email", defaultValue = "") String email,
                                       HttpSession session,
                                       Model model) {
        clearResetAuthorization(session);
        String normalizedEmail = OtpService.normalizeEmail(email);
        Object existingPendingEmail = session.getAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
        boolean samePendingEmail = existingPendingEmail instanceof String previousEmail
                && previousEmail.equals(normalizedEmail);
        if (!samePendingEmail) {
            session.removeAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
            otpService.clearOtp(session, Purpose.RESET_PASSWORD);
        }

        if (!normalizedEmail.isBlank()) {
            User user = userRepository.findByEmailIgnoreCase(normalizedEmail);
            if (user != null) {
                try {
                    otpService.sendOtp(session, Purpose.RESET_PASSWORD, normalizedEmail);
                    session.setAttribute(PENDING_RESET_EMAIL_ATTRIBUTE, normalizedEmail);
                } catch (OtpException ignored) {
                    // Keep the response identical for known and unknown addresses.
                }
            } else {
                session.removeAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
                otpService.clearOtp(session, Purpose.RESET_PASSWORD);
            }
        } else {
            session.removeAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
            otpService.clearOtp(session, Purpose.RESET_PASSWORD);
        }

        model.addAttribute("message", GENERIC_MESSAGE);
        model.addAttribute("otpStep", true);
        model.addAttribute("resendWaitSeconds", OtpService.RESEND_INTERVAL.getSeconds());
        return "forgot_password";
    }

    @PostMapping("/forgot-password/resend")
    public String resendResetOtp(HttpSession session, Model model) {
        Object pendingEmail = session.getAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
        if (pendingEmail instanceof String email) {
            User user = userRepository.findByEmailIgnoreCase(email);
            if (user != null) {
                try {
                    otpService.sendOtp(session, Purpose.RESET_PASSWORD, email);
                } catch (OtpException ignored) {
                    // Do not disclose whether the address is registered or rate-limited.
                }
            }
        }

        model.addAttribute("message", GENERIC_MESSAGE);
        model.addAttribute("otpStep", true);
        model.addAttribute("resendWaitSeconds", OtpService.RESEND_INTERVAL.getSeconds());
        return "forgot_password";
    }

    @PostMapping("/forgot-password/verify-otp")
    public String verifyResetOtp(@RequestParam(value = "otp", defaultValue = "") String code,
                                 HttpSession session,
                                 Model model) {
        Object pendingEmail = session.getAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
        Optional<String> verifiedEmail = otpService.verifyOtp(session, Purpose.RESET_PASSWORD, code);
        if (!(pendingEmail instanceof String email)
                || verifiedEmail.isEmpty()
                || !email.equals(verifiedEmail.get())) {
            model.addAttribute("otpStep", true);
            model.addAttribute("otpResult", "error");
            model.addAttribute("resendWaitSeconds", OtpService.RESEND_INTERVAL.getSeconds());
            model.addAttribute("error", OTP_ERROR);
            return "forgot_password";
        }

        createResetAuthorization(session, email);
        model.addAttribute("resetToken", session.getAttribute(RESET_TOKEN_ATTRIBUTE));
        model.addAttribute("otpVerified", true);
        return "reset_password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(HttpSession session, Model model) {
        Object token = session.getAttribute(RESET_TOKEN_ATTRIBUTE);
        Object email = session.getAttribute(RESET_EMAIL_ATTRIBUTE);
        Object createdAt = session.getAttribute(RESET_CREATED_AT_ATTRIBUTE);
        if (!(token instanceof String resetToken)
                || !(email instanceof String)
                || !(createdAt instanceof Instant instant)
                || !Instant.now().isBefore(instant.plus(RESET_TOKEN_TTL))) {
            clearResetAuthorization(session);
            return "redirect:/forgot-password";
        }

        model.addAttribute("resetToken", resetToken);
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(value = "resetToken", required = false) String submittedToken,
                                @RequestParam(value = "newPassword", defaultValue = "") String newPassword,
                                HttpSession session,
                                Model model) {
        Object storedToken = session.getAttribute(RESET_TOKEN_ATTRIBUTE);
        Object emailValue = session.getAttribute(RESET_EMAIL_ATTRIBUTE);
        Object createdAtValue = session.getAttribute(RESET_CREATED_AT_ATTRIBUTE);

        if (!(storedToken instanceof String expectedToken)
                || !(emailValue instanceof String email)
                || !(createdAtValue instanceof Instant createdAt)
                || !isValidToken(submittedToken, expectedToken)
                || !Instant.now().isBefore(createdAt.plus(RESET_TOKEN_TTL))) {
            clearResetAuthorization(session);
            model.addAttribute("error", INVALID_RESET_ERROR);
            return "reset_password";
        }

        model.addAttribute("resetToken", expectedToken);
        if (!PasswordPolicy.isValid(newPassword)) {
            model.addAttribute("error",
                    "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.");
            return "reset_password";
        }

        User user = userRepository.findByEmailIgnoreCase(email);
        if (user == null) {
            clearResetAuthorization(session);
            model.addAttribute("error", INVALID_RESET_ERROR);
            return "reset_password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        clearResetAuthorization(session);

        model.addAttribute("message", "Đổi mật khẩu thành công! Bạn có thể đăng nhập ngay.");
        return "reset_password";
    }

    private void createResetAuthorization(HttpSession session, String email) {
        byte[] randomToken = new byte[32];
        SECURE_RANDOM.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        session.setAttribute(RESET_TOKEN_ATTRIBUTE, token);
        session.setAttribute(RESET_EMAIL_ATTRIBUTE, email);
        session.setAttribute(RESET_CREATED_AT_ATTRIBUTE, Instant.now());
        session.removeAttribute(PENDING_RESET_EMAIL_ATTRIBUTE);
    }

    private boolean isValidToken(String submittedToken, String expectedToken) {
        return submittedToken != null && MessageDigest.isEqual(
                submittedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private void clearResetAuthorization(HttpSession session) {
        session.removeAttribute(RESET_TOKEN_ATTRIBUTE);
        session.removeAttribute(RESET_EMAIL_ATTRIBUTE);
        session.removeAttribute(RESET_CREATED_AT_ATTRIBUTE);
    }
}
