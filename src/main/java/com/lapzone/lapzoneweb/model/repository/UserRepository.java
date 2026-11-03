package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    User findByEmail(String email);
    User findByPhone(String phone);
    User findByEmailOrPhone(String email, String phone);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    User findByEmailAndPhone(String email, String phone);

    // [MỚI - FIX HIỆU SUẤT] Tìm kiếm có phân trang: chỉ load 20 users/trang
    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR " +
           " LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsersPaged(@Param("keyword") String keyword, Pageable pageable);
}