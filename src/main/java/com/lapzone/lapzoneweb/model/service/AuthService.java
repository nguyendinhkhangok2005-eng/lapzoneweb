package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.UserRegisterDTO;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String emailOrPhone, String password) {
        User user = userRepository.findByEmailOrPhone(emailOrPhone, emailOrPhone);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
    public boolean register(UserRegisterDTO registerDTO) {
        if (!isRegistrationAvailable(registerDTO.getEmail(), registerDTO.getPhone())) {
            return false;
        }

        return registerEncoded(registerDTO.getFullName(), registerDTO.getEmail(), registerDTO.getPhone(),
                registerDTO.getAddress(), passwordEncoder.encode(registerDTO.getPassword()));
    }

    public boolean isRegistrationAvailable(String email, String phone) {
        return !userRepository.existsByEmailIgnoreCase(OtpService.normalizeEmail(email))
                && !userRepository.existsByPhone(phone);
    }

    public boolean registerEncoded(String fullName, String email, String phone, String address,
                                   String encodedPassword) {
        if (!isRegistrationAvailable(email, phone)) {
            return false;
        }

        User newUser = new User();
        newUser.setFullName(fullName);
        newUser.setEmail(OtpService.normalizeEmail(email));
        newUser.setPhone(phone);
        newUser.setAddress(address);
        newUser.setPassword(encodedPassword);
        newUser.setRole("USER");
        userRepository.save(newUser);
        return true;
    }
}