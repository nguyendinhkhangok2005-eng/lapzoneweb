package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.UserRegisterDTO;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public User authenticate(String emailOrPhone, String password) {
        User user = userRepository.findByEmailOrPhone(emailOrPhone, emailOrPhone);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
    public boolean register(UserRegisterDTO registerDTO) {
        // Kiểm tra trùng lặp email hoặc số điện thoại
        if (userRepository.existsByEmail(registerDTO.getEmail()) || 
            userRepository.existsByPhone(registerDTO.getPhone())) {
            return false;
        }

        // Mapping dữ liệu từ DTO sang Entity
        User newUser = new User();
        newUser.setFullName(registerDTO.getFullName());
        newUser.setEmail(registerDTO.getEmail());
        newUser.setPhone(registerDTO.getPhone());
        newUser.setAddress(registerDTO.getAddress());
        // Mã hóa mật khẩu trước khi lưu
        newUser.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        
        // Thiết lập role mặc định
        newUser.setRole("USER"); 
        
        userRepository.save(newUser);
        return true;
    }
}