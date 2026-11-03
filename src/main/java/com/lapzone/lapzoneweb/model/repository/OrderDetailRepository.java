package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    // Lấy ra danh sách các máy tính trong một đơn hàng cụ thể
    List<OrderDetail> findByOrderId(Long orderId);
}