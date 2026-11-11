package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.CartController;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.security.CustomAuthenticationSuccessHandler;
import com.lapzone.lapzoneweb.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;

    @Test
    void unauthenticatedCartRequestRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dangnhap"));
    }

    @Test
    void unauthenticatedRemovalCannotReachCartService() throws Exception {
        mockMvc.perform(post("/cart/remove").header("X-Requested-With", "XMLHttpRequest")
                        .param("cartItemId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dangnhap"));
        org.mockito.Mockito.verify(cartService, org.mockito.Mockito.never()).removeFromCart(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unauthenticatedProfileRequestRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dangnhap"));
    }

    @Test
    void buyNowCheckoutRemainsPublic() throws Exception {
        mockMvc.perform(get("/checkout").param("mode", "buynow"))
                .andExpect(status().isNotFound());
    }
}
