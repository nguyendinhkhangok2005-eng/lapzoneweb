package com.lapzone.lapzoneweb;


import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lapzone.lapzoneweb.model.service.AuthService;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.model.entity.User;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;
    
    //TH1: Tài khoản và mật khẩu đúng
    @Test
    public void testAuthenticateSuccess() {
        User mockUser = new User();
        mockUser.setPassword("test-password");

        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(mockUser);
        User result = authService.authenticate("phileevaan@gmail.com", "test-password");
        assertNotNull(result);
    }

    //TH2: Sai mật khẩu
    @Test
    public void testAuthenticateFailed() {
        User mockUser = new User();
        mockUser.setPassword("stored-password-fixture");

        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(mockUser);
        User result = authService.authenticate("phileevaan@gmail.com", "test-password");
        assertNull(result);
    }

    //TH3: Tài khoản không tồn tại
    @Test
    public void testAuthenticateNotFound() {
        when(userRepository.findByEmailOrPhone("phileevaan@gmail.com", "phileevaan@gmail.com")).thenReturn(null);
        User result = authService.authenticate("phileevaan@gmail.com", "test-password");
        assertNull(result);
    }

    @Test
    public void registerEncodedStoresOnlyEncodedPasswordWithUserRole() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);

        boolean registered = authService.registerEncoded("Test User", "user@example.com",
                "0912345678", "Address", "$2a$10$encoded");

        assertEquals(true, registered);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("$2a$10$encoded", userCaptor.getValue().getPassword());
        assertEquals("USER", userCaptor.getValue().getRole());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
    }

    @Test
    public void registerEncodedRejectsEmailRegisteredWhileOtpWasPending() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        boolean registered = authService.registerEncoded("Test User", "user@example.com",
                "0912345678", "Address", "$2a$10$encoded");

        assertEquals(false, registered);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verify(userRepository, times(1)).existsByEmailIgnoreCase("user@example.com");
    }

}


