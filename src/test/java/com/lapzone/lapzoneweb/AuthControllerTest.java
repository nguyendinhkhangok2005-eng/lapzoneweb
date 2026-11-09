package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AuthController;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.AuthService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String PASSWORD = "StrongPass1!";
    private static final String HASHED_PASSWORD = "$2a$10$mock-bcrypt-hash";
    private static final String EMAIL = "user@example.com";
    private static final String PHONE = "0912345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;

    @Test
    void registrationDoesNotCreateUserBeforeOtpAndStoresOnlyPasswordHashInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(authService.isRegistrationAvailable(EMAIL, PHONE)).thenReturn(true);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);

        mockMvc.perform(register(session))
                .andExpect(status().isOk())
                .andExpect(view().name("verify_registration_otp"));

        assertNotNull(session.getAttribute("pendingRegistration"));
        String pending = session.getAttribute("pendingRegistration").toString();
        org.junit.jupiter.api.Assertions.assertTrue(pending.contains(HASHED_PASSWORD));
        org.junit.jupiter.api.Assertions.assertFalse(pending.contains(PASSWORD));
        verify(authService, never()).registerEncoded(any(), any(), any(), any(), any());
        verify(otpService).sendOtp(any(HttpSession.class), eq(OtpService.Purpose.REGISTER), eq(EMAIL));
    }

    @Test
    void validOtpCreatesUserWithBcryptAndClearsPendingSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(authService.isRegistrationAvailable(EMAIL, PHONE)).thenReturn(true);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
        mockMvc.perform(register(session)).andExpect(view().name("verify_registration_otp"));
        when(otpService.verifyOtp(any(HttpSession.class), eq(OtpService.Purpose.REGISTER), eq("123456")))
                .thenReturn(Optional.of(EMAIL));
        when(authService.registerEncoded("Test User", EMAIL, PHONE, "Address", HASHED_PASSWORD)).thenReturn(true);

        mockMvc.perform(post("/dangki/xac-thuc").session(session).param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify_registration_otp"))
                .andExpect(model().attributeExists("success"))
                .andExpect(model().attribute("otpVerified", true));

        verify(authService).registerEncoded("Test User", EMAIL, PHONE, "Address", HASHED_PASSWORD);
        assertNull(session.getAttribute("pendingRegistration"));
    }

    @Test
    void duplicateEmailOrPhoneIsRejectedBeforeSendingOtp() throws Exception {
        when(authService.isRegistrationAvailable(EMAIL, PHONE)).thenReturn(false);

        mockMvc.perform(register(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("dangki"))
                .andExpect(model().attributeExists("error"));

        verify(otpService, never()).sendOtp(any(HttpSession.class), any(), any());
    }

    @Test
    void registrationIsRecheckedBeforeCreatingUserAfterOtp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(authService.isRegistrationAvailable(EMAIL, PHONE)).thenReturn(true);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
        mockMvc.perform(register(session)).andExpect(view().name("verify_registration_otp"));
        when(otpService.verifyOtp(any(HttpSession.class), eq(OtpService.Purpose.REGISTER), eq("123456")))
                .thenReturn(Optional.of(EMAIL));
        when(authService.registerEncoded("Test User", EMAIL, PHONE, "Address", HASHED_PASSWORD)).thenReturn(false);

        mockMvc.perform(post("/dangki/xac-thuc").session(session).param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("dangki"))
                .andExpect(model().attributeExists("error"));

        assertNull(session.getAttribute("pendingRegistration"));
    }

    @Test
    void directRegistrationOtpPageWithoutPendingRegistrationRedirectsToRegister() throws Exception {
        mockMvc.perform(get("/dangki/xac-thuc").session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dangki"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            MockHttpSession session) {
        return post("/dangki").session(session)
                .param("fullName", "Test User")
                .param("email", EMAIL)
                .param("phone", PHONE)
                .param("address", "Address")
                .param("password", PASSWORD)
                .param("confirmPassword", PASSWORD);
    }
}
