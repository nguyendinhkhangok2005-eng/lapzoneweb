package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.CartController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartRemovalControllerTest {
    @Mock CartService cartService;
    @InjectMocks CartController controller;
    MockMvc mvc;
    MockHttpSession session;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        session = new MockHttpSession();
        User user = new User();
        user.setId(7L);
        session.setAttribute("currentUser", user);
    }

    @Test
    void removesOnlyRequestedItemForSessionUser() throws Exception {
        when(cartService.removeFromCart(7L, 12L)).thenReturn(new CartService.RemovalResult(3, 45000000));
        mvc.perform(post("/cart/remove").session(session).header("X-Requested-With", "XMLHttpRequest")
                        .param("cartItemId", "12").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItemCount").value(3))
                .andExpect(jsonPath("$.cartTotal").value(45000000))
                .andExpect(jsonPath("$.message").value("Đã xóa sản phẩm khỏi giỏ hàng."));
        verify(cartService).removeFromCart(7L, 12L);
    }

    @Test
    void missingOrForeignItemReturnsSameSafeError() throws Exception {
        mvc.perform(post("/cart/remove").session(session).header("X-Requested-With", "XMLHttpRequest")
                        .param("cartItemId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm trong giỏ hàng của bạn."));
    }

    @Test
    void invalidIdsNeverReachService() throws Exception {
        for (String id : new String[]{"", "abc", "0", "-1", "9223372036854775808"}) {
            mvc.perform(post("/cart/remove").session(session).header("X-Requested-With", "XMLHttpRequest")
                            .param("cartItemId", id))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/cart/remove").session(session).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }

    @Test
    void expiredSessionCannotRemoveAnything() throws Exception {
        mvc.perform(post("/cart/remove").header("X-Requested-With", "XMLHttpRequest").param("cartItemId", "12"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cartService);
    }

    @Test
    void crossSiteFormCannotRemoveAnything() throws Exception {
        mvc.perform(post("/cart/remove").session(session).param("cartItemId", "12"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);
    }

    @Test
    void databaseFailureReturnsVietnameseError() throws Exception {
        when(cartService.removeFromCart(7L, 12L)).thenThrow(new IllegalStateException("database unavailable"));
        mvc.perform(post("/cart/remove").session(session).header("X-Requested-With", "XMLHttpRequest")
                        .param("cartItemId", "12"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Không thể xóa sản phẩm. Vui lòng thử lại."));
    }
}
