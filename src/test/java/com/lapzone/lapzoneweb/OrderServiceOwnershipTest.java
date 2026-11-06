package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.OrderController;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceOwnershipTest {

    private static final Long ORDER_ID = 42L;
    private static final String VERIFIED_ORDER_ID_SESSION_ATTRIBUTE = "verifiedOrderId";

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private Order order;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(7L);
        owner.setRole("USER");

        order = new Order();
        order.setId(ORDER_ID);
        order.setUser(owner);
        order.setCustomerPhone("0900000000");
    }

    @Test
    void orderOwnerCanViewOrder() {
        when(orderService.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("order_detail", orderController.viewOrderDetail(ORDER_ID, model,
                new MockHttpSession(), authenticated(owner, "ROLE_USER")));
        assertEquals(order, model.get("order"));
    }

    @Test
    void differentUserCannotViewOrder() {
        when(orderService.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        User otherUser = new User();
        otherUser.setId(8L);
        otherUser.setRole("USER");

        assertNotFound(() -> viewAs(ORDER_ID, authenticated(otherUser, "ROLE_USER"),
                new MockHttpSession()));
    }

    @Test
    void guestWithoutSuccessfulLookupCannotViewOrder() {
        when(orderService.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));

        assertNotFound(() -> viewAs(ORDER_ID, null, new MockHttpSession()));
    }

    @Test
    void guestCanViewOrderAfterMatchingLookup() {
        when(orderService.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        MockHttpSession session = new MockHttpSession();
        String lookupResult = orderController.processLookup(ORDER_ID, " 0900000000 ",
                session, new RedirectAttributesModelMap());

        assertEquals("redirect:/order/detail/" + ORDER_ID, lookupResult);
        assertEquals(ORDER_ID, session.getAttribute(VERIFIED_ORDER_ID_SESSION_ATTRIBUTE));
        assertEquals("order_detail", viewAs(ORDER_ID, null, session));
    }

    @Test
    void adminCanViewAnyOrder() {
        when(orderService.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        User admin = new User();
        admin.setId(9L);
        admin.setRole("ADMIN");

        assertEquals("order_detail", viewAs(ORDER_ID, authenticated(admin, "ROLE_ADMIN"),
                new MockHttpSession()));
    }

    private String viewAs(Long orderId, Authentication authentication, MockHttpSession session) {
        return orderController.viewOrderDetail(orderId, new ExtendedModelMap(), session, authentication);
    }

    private Authentication authenticated(User user, String authority) {
        CustomUserDetails principal = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(authority)));
    }

    private void assertNotFound(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
