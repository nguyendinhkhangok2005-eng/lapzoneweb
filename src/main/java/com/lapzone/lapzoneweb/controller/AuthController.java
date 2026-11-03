package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.dto.UserRegisterDTO;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;


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
                                 Model model) {
        
        // 1. Kiểm tra lỗi định dạng từ DTO
        if (result.hasErrors()) {
            return "dangki";
        }

        // 2. Kiểm tra mật khẩu khớp nhau
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
            return "dangki";
        }

        // 3. Thực hiện đăng ký qua Service
        if (authService.register(registerDTO)) {
            model.addAttribute("success", "Đăng ký thành công! Hãy đăng nhập.");
            return "dangnhap";
        }
        
        model.addAttribute("error", "Email hoặc Số điện thoại đã tồn tại!");
        return "dangki";
    }

}
