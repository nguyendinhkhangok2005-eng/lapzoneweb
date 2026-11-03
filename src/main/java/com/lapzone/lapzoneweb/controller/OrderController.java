package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class OrderController {

    private static final String VERIFIED_ORDER_ID_SESSION_ATTRIBUTE = "verifiedOrderId";

    @Autowired
    private OrderService orderService;

    // 1. Hiển thị trang nhập thông tin tra cứu
    @GetMapping("/tracuudonhang")
    public String showLookupPage() {
        return "order_lookup"; 
    }

    // 2. Xử lý khi khách hàng bấm "Tra cứu"
    @PostMapping("/tracuudonhang")
    public String processLookup(@RequestParam("orderId") Long orderId,
                                @RequestParam("customerPhone") String customerPhone,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Optional<Order> foundOrder = orderService.findOrderById(orderId);
        if (foundOrder.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy mã đơn hàng #" + orderId);
            return "redirect:/tracuudonhang";
        }
        Order order = foundOrder.get();

        // Bảo mật: Kiểm tra xem Số điện thoại khách nhập có KHỚP với SĐT lúc đặt hàng không
        if (customerPhone != null && customerPhone.trim().equals(order.getCustomerPhone())) {
            // Chỉ ghi nhận đơn vừa tìm thấy sau khi mã đơn và số điện thoại cùng khớp
            session.setAttribute(VERIFIED_ORDER_ID_SESSION_ATTRIBUTE, order.getId());
            return "redirect:/order/detail/" + order.getId();
        }

        redirectAttributes.addFlashAttribute("error", "Số điện thoại không khớp với đơn hàng này!");
        return "redirect:/tracuudonhang";
    }

    // 3. Hiển thị tờ Hóa đơn (Chức năng bạn đã hỏi ở turn trước)
    @GetMapping("/order/detail/{id}")
    public String viewOrderDetail(@PathVariable("id") Long id, Model model,
                                  HttpSession session, Authentication authentication) {
        Order order = orderService.findOrderById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canViewOrder(order, session, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("order", order);
        return "order_detail"; // Trỏ tới file order_detail.html mà anh em mình thiết kế lúc nãy
    }

    private boolean canViewOrder(Order order, HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return order.getId().equals(session.getAttribute(VERIFIED_ORDER_ID_SESSION_ATTRIBUTE));
        }

        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return true;
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && order.getUser() != null
                && userDetails.getUser().getId() != null) {
            return userDetails.getUser().getId().equals(order.getUser().getId());
        }

        return false;
    }
}