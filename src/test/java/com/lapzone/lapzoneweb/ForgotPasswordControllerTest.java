package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.ForgotPasswordController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.OtpService;
import com.lapzone.lapzoneweb.security.CustomAuthenticationSuccessHandler;
import com.lapzone.lapzoneweb.security.SecurityConfig;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = ForgotPasswordController.class)
@Import(SecurityConfig.class)
class ForgotPasswordControllerTest {

    private static final String VALID_PASSWORD = "StrongPass1!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;

    @Test
    void unknownEmailReceivesGenericMessageAndNoEmailIsSent() throws Exception {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(null);

        mockMvc.perform(post("/forgot-password")
                        .session(new MockHttpSession())
                        .param("email", "unknown@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attribute("message", "Nếu email tồn tại, mã xác nhận đã được gửi."))
                .andExpect(model().attribute("otpStep", true));

        verify(otpService, never()).sendOtp(any(HttpSession.class),
                eq(OtpService.Purpose.RESET_PASSWORD), any());
    }

    @Test
    void resetWithoutOtpIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/reset-password").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"));
        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password"))
                .andExpect(model().attributeExists("error"));

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void otpForEmailAResetsOnlyEmailAAndIgnoresSubmittedEmailB() throws Exception {
        User userA = user("a@example.com", "a-original-hash");
        User userB = user("b@example.com", "b-original-hash");
        MockHttpSession session = new MockHttpSession();
        requestOtp(session, userA);
        when(otpService.verifyOtp(any(HttpSession.class), eq(OtpService.Purpose.RESET_PASSWORD), eq("123456")))
                .thenReturn(Optional.of("a@example.com"));
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(userA);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("a-bcrypt-hash");

        String resetToken = verifyOtp(session);
        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", resetToken)
                        .param("email", userB.getEmail())
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("message"));

        assertEquals("a-bcrypt-hash", userA.getPassword());
        assertEquals("b-original-hash", userB.getPassword());
        verify(userRepository, times(2)).findByEmailIgnoreCase(userA.getEmail());
        verify(userRepository, never()).findByEmailIgnoreCase(userB.getEmail());
        verify(userRepository, never()).save(userB);
    }

    @Test
    void successfulResetTokenCanOnlyBeUsedOnce() throws Exception {
        User user = user("a@example.com", "original-hash");
        MockHttpSession session = new MockHttpSession();
        requestOtp(session, user);
        when(otpService.verifyOtp(any(HttpSession.class), eq(OtpService.Purpose.RESET_PASSWORD), eq("123456")))
                .thenReturn(Optional.of(user.getEmail()));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(user);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");
        String resetToken = verifyOtp(session);

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", resetToken)
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("message"));
        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", resetToken)
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        assertEquals("bcrypt-hash", user.getPassword());
        assertNull(session.getAttribute("passwordResetToken"));
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("passwordResetToken", "valid-token");
        session.setAttribute("passwordResetEmail", "a@example.com");
        session.setAttribute("passwordResetCreatedAt",
                Instant.now().minusSeconds(16 * 60L));

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", "valid-token")
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void weakPasswordDoesNotConsumeValidResetToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("passwordResetToken", "valid-token");
        session.setAttribute("passwordResetEmail", "a@example.com");
        session.setAttribute("passwordResetCreatedAt", Instant.now());

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", "valid-token")
                        .param("newPassword", "weakpass"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        assertNotNull(session.getAttribute("passwordResetToken"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void validOtpCreatesSessionTokenAndResetsPassword() throws Exception {
        User user = user("a@example.com", "original-hash");
        MockHttpSession session = new MockHttpSession();
        requestOtp(session, user);
        when(otpService.verifyOtp(any(HttpSession.class), eq(OtpService.Purpose.RESET_PASSWORD), eq("123456")))
                .thenReturn(Optional.of(user.getEmail()));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(user);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");

        String resetToken = verifyOtp(session);
        mockMvc.perform(get("/reset-password").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password"))
                .andExpect(model().attribute("resetToken", resetToken));
        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("resetToken", resetToken)
                        .param("newPassword", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("message"));

        assertEquals("bcrypt-hash", user.getPassword());
        assertNull(session.getAttribute("passwordResetToken"));
        verify(userRepository).save(user);
    }

    private void requestOtp(MockHttpSession session, User user) throws Exception {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(user);
        mockMvc.perform(post("/forgot-password")
                        .session(session)
                        .param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attribute("message", "Nếu email tồn tại, mã xác nhận đã được gửi."));
        assertEquals(user.getEmail(), session.getAttribute("pendingPasswordResetEmail"));
        verify(otpService).sendOtp(any(HttpSession.class), eq(OtpService.Purpose.RESET_PASSWORD), eq(user.getEmail()));
    }

    private String verifyOtp(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/forgot-password/verify-otp")
                        .session(session)
                        .param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password"))
                .andExpect(model().attributeExists("resetToken"))
                .andExpect(model().attribute("otpVerified", true))
                .andReturn();
        return (String) result.getRequest().getSession().getAttribute("passwordResetToken");
    }

    private User user(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }
}
