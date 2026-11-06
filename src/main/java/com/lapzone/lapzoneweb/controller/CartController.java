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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class CartController {

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
    public String removeItem(@RequestParam("cartItemId") Long cartItemId, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/dangnhap";
        }
        cartService.removeFromCart(currentUser.getId(), cartItemId);
        return "redirect:/cart";
    }
}