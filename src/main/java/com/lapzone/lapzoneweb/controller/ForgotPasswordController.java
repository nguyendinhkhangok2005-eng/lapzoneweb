package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ForgotPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // 1. Hiện form nhập Email & Số điện thoại
    @GetMapping("/forgot-password")
    public String showForgotForm() {
        return "forgot_password";
    }

    // 2. Kiểm tra xem Email và SĐT có khớp không
    @PostMapping("/verify-phone")
    public String verifyPhone(@RequestParam("email") String email, 
                              @RequestParam("phoneNumber") String phoneNumber, 
                              Model model) {
        
        User user = userRepository.findByEmailAndPhone(email, phoneNumber);
        
        if (user != null) {
            // Nếu khớp -> Chuyển sang trang đặt mật khẩu mới, truyền Email theo để nhớ
            model.addAttribute("verifiedEmail", email);
            return "reset_password";
        } else {
            // Không khớp -> Báo lỗi
            model.addAttribute("error", "Email hoặc Số điện thoại không chính xác!");
            return "forgot_password";
        }
    }

    // 3. Xử lý lưu mật khẩu mới
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("email") String email, 
                                @RequestParam("newPassword") String newPassword, 
                                Model model) {
        User user = userRepository.findByEmail(email);
        
        if (user != null) {
            // Đã kích hoạt mã hóa BCrypt
            user.setPassword(passwordEncoder.encode(newPassword)); 
            userRepository.save(user);
            
            model.addAttribute("message", "Đổi mật khẩu thành công! Bạn có thể đăng nhập ngay.");
            return "reset_password"; 
        }
        return "redirect:/login";
    }
}