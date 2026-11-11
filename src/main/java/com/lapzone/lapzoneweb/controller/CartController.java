package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class CartController {
    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartService cartService;

    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            return "redirect:/dangnhap"; 
        }

        List<CartItem> cartItems = cartService.getCartItems(currentUser.getId());
        Double total = cartService.getCartTotal(currentUser.getId());

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("total", total);

        return "cart"; 
    }
    @PostMapping("/cart/add")
    public String addToCart(@RequestParam("productId") Long productId,
                            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/dangnhap";
        }

        if (cartService.addToCart(currentUser.getId(), productId, quantity)) {
            redirectAttributes.addFlashAttribute("cartSuccess", "Đã thêm sản phẩm vào giỏ hàng.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Không thể thêm sản phẩm. Vui lòng kiểm tra tồn kho và số lượng.");
        }
        return "redirect:/cart"; 
    }
    @PostMapping("/cart/update")
    public String updateQuantity(@RequestParam("cartItemId") Long cartItemId,
                                 @RequestParam("quantity") Integer quantity,
                                 HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/dangnhap";
        }
        cartService.updateQuantity(currentUser.getId(), cartItemId, quantity);
        return "redirect:/cart";
    }
    @PostMapping("/cart/remove")
    @ResponseBody
    public ResponseEntity<?> removeItem(@RequestParam(value = "cartItemId", required = false) String cartItemId,
            @RequestHeader(value = "X-Requested-With", defaultValue = "") String requestedWith,
            HttpSession session) {
        // Cross-origin HTML forms cannot set this header. Do not allow CORS on this operation.
        if (!"XMLHttpRequest".equals(requestedWith)) {
            return ResponseEntity.status(403).body(Map.of("message", "Yêu cầu xóa sản phẩm không hợp lệ."));
        }
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));
        }
        Long itemId;
        try {
            itemId = Long.valueOf(cartItemId == null ? "" : cartItemId);
            if (itemId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mã sản phẩm trong giỏ hàng không hợp lệ."));
        }
        try {
            CartService.RemovalResult result = cartService.removeFromCart(currentUser.getId(), itemId);
            if (result == null) {
                return ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy sản phẩm trong giỏ hàng của bạn."));
            }
            return ResponseEntity.ok(Map.of("message", "Đã xóa sản phẩm khỏi giỏ hàng.",
                    "cartItemCount", result.cartItemCount(), "cartTotal", result.cartTotal()));
        } catch (RuntimeException ex) {
            logger.error("Could not remove cart item {} for user {}", itemId, currentUser.getId(), ex);
            return ResponseEntity.internalServerError().body(Map.of("message",
                    "Không thể xóa sản phẩm. Vui lòng thử lại."));
        }
    }
}
