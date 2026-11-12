package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.OrderItemActionsController;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.OrderItemActionsService;
import com.lapzone.lapzoneweb.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderItemActionsController.class)
@Import(SecurityConfig.class)
class OrderItemActionsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderItemActionsService actions;
    @MockitoBean CartService carts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    MockHttpSession customer() {
        User user = new User(); user.setId(10L); user.setRole("USER"); user.setEmail("khach@example.test");
        var principal = new CustomUserDetails(user); var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())));
        return session;
    }

    @Test void reviewFormAndBothPostsRequireLoginAndCsrf() throws Exception {
        mvc.perform(get("/store/orders/7/items/8/review")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/store/orders/7/items/8/review").session(customer()).param("rating", "5").param("content", "Tốt"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/store/orders/7/items/8/repurchase").session(customer()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(actions);
    }

    @Test void submittedReviewAndRepurchaseUseOnlyOwnedPathAndRedirectCorrectly() throws Exception {
        var session = customer();
        when(actions.eligibleReview(7L, 8L)).thenReturn(new OrderItemActionsService.ReviewItem(7L, 8L, "Máy đã mua"));
        var result = mvc.perform(get("/store/orders/7/items/8/review").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gửi đánh giá"))).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post("/store/orders/7/items/8/review").session(session).param(token.getParameterName(), token.getToken())
                .param("rating", "5").param("content", "Sản phẩm tốt").param("userId", "999"))
                .andExpect(redirectedUrl("/store/orders/7?category=REVIEW"));
        verify(actions).submitReview(7L, 8L, 5, "Sản phẩm tốt");
        mvc.perform(post("/store/orders/7/items/8/repurchase").session(session).param(token.getParameterName(), token.getToken())
                .param("productId", "999")).andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartSuccess", containsString("giá hiện tại")));
        verify(actions).repurchase(7L, 8L);
    }

    @Test void unavailableRepurchaseAndForeignReviewReturnVietnameseFeedback() throws Exception {
        var session = customer();
        when(actions.eligibleReview(7L, 8L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong đơn hàng của bạn."));
        var response = mvc.perform(get("/store/orders/7/items/8/review").session(session)).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Không tìm thấy sản phẩm trong đơn hàng của bạn."))).andReturn();
        CsrfToken token = (CsrfToken) response.getRequest().getAttribute("_csrf");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm hiện không còn bán hoặc đã hết hàng."))
                .when(actions).repurchase(7L, 8L);
        mvc.perform(post("/store/orders/7/items/8/repurchase").session(session).param(token.getParameterName(), token.getToken()))
                .andExpect(redirectedUrl("/store/orders/7"))
                .andExpect(flash().attribute("error", "Sản phẩm hiện không còn bán hoặc đã hết hàng."));
    }
}
