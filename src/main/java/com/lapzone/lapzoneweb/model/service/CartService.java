package com.lapzone.lapzoneweb.model.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import java.util.List;

@Service
public class CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    // [FIX N+1] Dùng JOIN FETCH để lấy CartItem + Product trong 1 SQL
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserIdWithProduct(userId);
    }

    public boolean addToCart(Long userId, Long productId, Integer quantity) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null)
            return false;
        if (quantity == null || quantity <= 0)
            return false;
        if (product.getStock() != null && product.getStock() <= 0)
            return false;

        CartItem existingItem = cartItemRepository.findByUserIdAndProduct(userId, product);

        if (existingItem != null) {
            int currentQuantity = existingItem.getQuantity() != null ? existingItem.getQuantity() : 0;
            int newQuantity = limitQuantityByStock(currentQuantity + quantity, product);
            if (newQuantity <= currentQuantity)
                return false;
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
            return true;
        } else {
            int limitedQuantity = limitQuantityByStock(quantity, product);
            if (limitedQuantity <= 0)
                return false;

            CartItem newItem = new CartItem();
            newItem.setUserId(userId);
            newItem.setProduct(product);
            newItem.setQuantity(limitedQuantity);
            cartItemRepository.save(newItem);
            return true;
        }
    }

    public void updateQuantity(Long userId, Long cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId).orElse(null);
        if (item != null) {
            if (quantity == null || quantity <= 0) {
                cartItemRepository.deleteByIdAndUserId(cartItemId, userId);
            } else {
                int limitedQuantity = limitQuantityByStock(quantity, item.getProduct());
                if (limitedQuantity <= 0) {
                    cartItemRepository.deleteByIdAndUserId(cartItemId, userId);
                } else {
                    item.setQuantity(limitedQuantity);
                    cartItemRepository.save(item);
                }
            }
        }
    }

    private int limitQuantityByStock(int requestedQuantity, Product product) {
        if (product == null || product.getStock() == null) {
            return requestedQuantity;
        }
        return Math.min(requestedQuantity, Math.max(product.getStock(), 0));
    }

    public void removeFromCart(Long userId, Long cartItemId) {
        cartItemRepository.deleteByIdAndUserId(cartItemId, userId);
    }

    // [FIX N+1] Dùng JOIN FETCH: lấy Cart + Product trong 1 query, không lazy load
    public Double getCartTotal(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdWithProduct(userId);
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += PromotionService.getDiscountedPrice(item.getProduct()) * item.getQuantity();
        }
        return total;
    }

    // Lấy danh sách các CartItem dựa trên danh sách ID được truyền vào
    public List<CartItem> getSelectedCartItems(List<Long> cartItemIds, Long userId) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return List.of();
        }
        return cartItemRepository.findByIdInAndUserIdWithProduct(cartItemIds, userId);
    }

    // Tính tổng tiền chỉ cho những món được chọn
    public Double getSelectedCartTotal(List<Long> cartItemIds, Long userId) {
        List<CartItem> selectedItems = getSelectedCartItems(cartItemIds, userId);
        double total = 0.0;
        for (CartItem item : selectedItems) {
            total += PromotionService.getDiscountedPrice(item.getProduct()) * item.getQuantity();
        }
        return total;
    }
}
