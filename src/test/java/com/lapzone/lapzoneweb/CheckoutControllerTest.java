package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.CheckoutController;
import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.model.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @BeforeEach @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    void login(User user) {
        user.setRole("USER");
        var principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @Mock
    private CartService cartService;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CheckoutController checkoutController;

    @Test
    void cartCheckoutWithNoOwnedItemsReturnsToCartWithoutCreatingOrder() {
        User user = new User();
        user.setId(7L);
        login(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        when(cartService.getSelectedCartItems(List.of(81L), 7L)).thenReturn(List.of());
        RedirectAttributesModelMap redirects = new RedirectAttributesModelMap();

        String result = checkoutController.showCheckoutPage(List.of(81L), null, session,
                new ExtendedModelMap(), redirects);

        assertEquals("redirect:/cart", result);
        assertEquals("Không tìm thấy sản phẩm hợp lệ trong giỏ hàng của bạn.", redirects.getFlashAttributes().get("error"));
        verify(orderService, never()).placeOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void guestCannotProcessCartCheckout() {
        MockHttpSession session = new MockHttpSession();

        String result = checkoutController.processCheckout("Guest", "0900", "Address", "COD",
                List.of(81L), "cart", session, new RedirectAttributesModelMap());

        assertEquals("redirect:/dangnhap", result);
        verify(orderService, never()).placeOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void cartCheckoutWithEmptyIdsReturnsToCartWithoutCreatingOrder() {
        User user = new User();
        user.setId(7L);
        login(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        RedirectAttributesModelMap redirects = new RedirectAttributesModelMap();

        String result = checkoutController.processCheckout("User", "0900", "Address", "COD",
                List.of(), "cart", session, redirects);

        assertEquals("redirect:/cart", result);
        assertEquals("Vui lòng chọn ít nhất 1 sản phẩm để thanh toán.", redirects.getFlashAttributes().get("error"));
        verify(orderService, never()).placeOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void cartCheckoutRejectsWhenServiceFindsNoItemsOwnedByCurrentUser() {
        User user = new User();
        user.setId(7L);
        login(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        when(orderService.placeOrder(eq(user), eq("User"), eq("0900"), eq("Address"),
                eq(List.of(81L)), eq("COD"))).thenThrow(new IllegalArgumentException("Không tìm thấy sản phẩm hợp lệ."));
        RedirectAttributesModelMap redirects = new RedirectAttributesModelMap();

        String result = checkoutController.processCheckout("User", "0900", "Address", "COD",
                List.of(81L), "cart", session, redirects);

        assertEquals("redirect:/cart", result);
        assertEquals("Có lỗi xảy ra: Không tìm thấy sản phẩm hợp lệ.", redirects.getFlashAttributes().get("error"));
        verify(orderService).placeOrder(user, "User", "0900", "Address", List.of(81L), "COD");
    }

    @Test
    void guestBuyNowIgnoresClientCartIdsAndStoresOnlySuccessfulOrderId() {
        Product sessionProduct = new Product();
        sessionProduct.setId(3L);
        CartItem sessionItem = new CartItem();
        sessionItem.setProduct(sessionProduct);
        sessionItem.setQuantity(1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUY_NOW_ITEM", sessionItem);
        Order savedOrder = new Order();
        savedOrder.setId(42L);
        when(orderService.placeOrderBuyNow(isNull(), eq("Guest"), eq("0900"), eq("Address"),
                eq(sessionItem), eq("COD"))).thenReturn(savedOrder);

        String result = checkoutController.processCheckout("Guest", "0900", "Address", "COD",
                List.of(999L), "buynow", session, new RedirectAttributesModelMap());

        assertEquals("redirect:/checkout/success", result);
        assertNull(session.getAttribute("BUY_NOW_ITEM"));
        assertEquals(42L, session.getAttribute("CHECKOUT_SUCCESS_ORDER_ID"));
        verify(orderService, never()).placeOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void guestSuccessPageRequiresTheOrderCreatedInThatSession() {
        MockHttpSession session = new MockHttpSession();
        assertEquals("redirect:/", checkoutController.showSuccessPage(session, new ExtendedModelMap()));

        session.setAttribute("CHECKOUT_SUCCESS_ORDER_ID", 42L);
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("checkout_success", checkoutController.showSuccessPage(session, model));
        assertEquals(42L, model.get("successOrderId"));
    }

    @Test
    void authenticatedBuyNowWithoutLegacySessionStillOwnsTheOrder() {
        User authenticated = new User(); authenticated.setId(7L); login(authenticated);
        assertCheckoutOwner(new MockHttpSession(), authenticated);
    }

    @Test
    void staleLegacySessionCannotAssignTheOrderToAnotherAccount() {
        User authenticated = new User(); authenticated.setId(7L); login(authenticated);
        User stale = new User(); stale.setId(99L);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("currentUser", stale);
        assertCheckoutOwner(session, authenticated);
    }

    @Test
    void sessionOnlyUserDoesNotClaimAGuestPurchase() {
        User stale = new User(); stale.setId(99L);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("currentUser", stale);
        assertCheckoutOwner(session, null);
    }

    void assertCheckoutOwner(MockHttpSession session, User expectedOwner) {
        Product product = new Product(); product.setId(3L);
        CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(1);
        session.setAttribute("BUY_NOW_ITEM", item);
        Order saved = new Order(); saved.setId(42L);
        when(orderService.placeOrderBuyNow(org.mockito.ArgumentMatchers.nullable(User.class), eq("Khách"), eq("0900"),
                eq("Địa chỉ"), eq(item), eq("COD"))).thenReturn(saved);
        assertEquals("redirect:/checkout/success", checkoutController.processCheckout("Khách", "0900", "Địa chỉ", "COD",
                List.of(), "buynow", session, new RedirectAttributesModelMap()));
        verify(orderService).placeOrderBuyNow(eq(expectedOwner), eq("Khách"), eq("0900"), eq("Địa chỉ"), eq(item), eq("COD"));
    }
}
