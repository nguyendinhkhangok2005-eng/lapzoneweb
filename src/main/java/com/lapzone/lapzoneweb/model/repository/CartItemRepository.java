package com.lapzone.lapzoneweb.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Product;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // [CŨ] Dùng Lazy Load - Có thể gây N+1 queries nếu không cẩn thận
    List<CartItem> findByUserId(Long userId);

    // [MỚI - FIX N+1] JOIN FETCH: Lấy CartItem + Product trong 1 câu SQL duy nhất
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.userId = :userId")
    List<CartItem> findByUserIdWithProduct(@Param("userId") Long userId);

    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.id IN :cartItemIds AND ci.userId = :userId")
    List<CartItem> findByIdInAndUserIdWithProduct(@Param("cartItemIds") List<Long> cartItemIds,
                                                  @Param("userId") Long userId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    int deleteByIdAndUserId(Long id, Long userId);

    CartItem findByUserIdAndProduct(Long userId, Product product);
    
    void deleteByUserId(Long userId);
}