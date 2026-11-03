package com.lapzone.lapzoneweb.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Product;
import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // [CŨ] Dùng Lazy Load - Có thể gây N+1 queries nếu không cẩn thận
    List<CartItem> findByUserId(Long userId);

    // [MỚI - FIX N+1] JOIN FETCH: Lấy CartItem + Product trong 1 câu SQL duy nhất
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.userId = :userId")
    List<CartItem> findByUserIdWithProduct(@Param("userId") Long userId);

    CartItem findByUserIdAndProduct(Long userId, Product product);
    
    void deleteByUserId(Long userId);
}