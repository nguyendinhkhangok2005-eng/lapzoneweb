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

    public void addToCart(Long userId, Long productId, Integer quantity) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null)
            return;
        if (quantity == null || quantity <= 0)
            return;

        CartItem existingItem = cartItemRepository.findByUserIdAndProduct(userId, product);

        if (existingItem != null) {
            int currentQuantity = existingItem.getQuantity() != null ? existingItem.getQuantity() : 0;
            existingItem.setQuantity(limitQuantityByStock(currentQuantity + quantity, product));
            cartItemRepository.save(existingItem);
        } else {
            int limitedQuantity = limitQuantityByStock(quantity, product);
            if (limitedQuantity <= 0)
                return;

            CartItem newItem = new CartItem();
            newItem.setUserId(userId);
            newItem.setProduct(product);
            newItem.setQuantity(limitedQuantity);
            cartItemRepository.save(newItem);
        }
    }

    public void updateQuantity(Long cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item != null) {
            if (quantity == null || quantity <= 0) {
                cartItemRepository.delete(item);
            } else {
                int limitedQuantity = limitQuantityByStock(quantity, item.getProduct());
                if (limitedQuantity <= 0) {
                    cartItemRepository.delete(item);
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

    public void removeFromCart(Long cartItemId) {
        cartItemRepository.deleteById(cartItemId);
    }

    // [FIX N+1] Dùng JOIN FETCH: lấy Cart + Product trong 1 query, không lazy load
    public Double getCartTotal(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdWithProduct(userId);
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += item.getProduct().getPrice() * item.getQuantity();
        }
        return total;
    }

    // Lấy danh sách các CartItem dựa trên danh sách ID được truyền vào
    public List<CartItem> getSelectedCartItems(List<Long> cartItemIds) {
        return cartItemRepository.findAllById(cartItemIds);
    }

    // Tính tổng tiền chỉ cho những món được chọn
    public Double getSelectedCartTotal(List<Long> cartItemIds) {
        List<CartItem> selectedItems = cartItemRepository.findAllById(cartItemIds);
        double total = 0.0;
        for (CartItem item : selectedItems) {
            total += item.getProduct().getPrice() * item.getQuantity();
        }
        return total;
    }
}
