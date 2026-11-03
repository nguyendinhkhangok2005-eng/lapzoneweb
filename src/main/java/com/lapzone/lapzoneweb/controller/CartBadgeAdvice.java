package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class CartBadgeAdvice {

    @Autowired
    private CartService cartService;
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @ModelAttribute("globalCategories")
    public List<Category> getGlobalCategories() {
        return categoryRepository.findAll();
    }

    @ModelAttribute("cartItemCount")
    public int getCartItemCount(HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser != null) {
            List<CartItem> items = cartService.getCartItems(currentUser.getId());
            int totalCount = 0;
            for (CartItem item : items) {
                totalCount += item.getQuantity();
            }
            return totalCount;
        }
        return 0;
    }
}