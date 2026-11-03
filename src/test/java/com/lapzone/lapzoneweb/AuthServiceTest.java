package com.lapzone.lapzoneweb;


import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.Test;

import com.lapzone.lapzoneweb.model.service.AuthService;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.model.entity.User;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;
    
    //TH1: Tài khoản và mật khẩu đúng
    @Test
    public void testAuthenticateSuccess() {
        User mockUser = new User();
        mockUser.setPassword("Dobietmatkhaudaycu01");

        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(mockUser);
        User result = authService.authenticate("phileevaan@gmail.com", "Dobietmatkhaudaycu01");
        assertNotNull(result);
    }

    //TH2: Sai mật khẩu
    @Test
    public void testAuthenticateFailed() {
        User mockUser = new User();
        mockUser.setPassword("Dobietmatkhaudaycu01@");

        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(mockUser);
        User result = authService.authenticate("phileevaan@gmail.com", "Dobietmatkhaudaycu01");
        assertNull(result);
    }

    //TH3: Tài khoản không tồn tại
    @Test
    public void testAuthenticateNotFound() {
        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(null);
        User result = authService.authenticate("phileevaan@gmail.com", "Dobietmatkhaudaycu01");
        assertNull(result);
    }

}



