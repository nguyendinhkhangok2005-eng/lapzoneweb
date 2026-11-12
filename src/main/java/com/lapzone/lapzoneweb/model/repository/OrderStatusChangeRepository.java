package com.lapzone.lapzoneweb.model.repository;
import com.lapzone.lapzoneweb.model.entity.OrderStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderStatusChangeRepository extends JpaRepository<OrderStatusChange, Long> {
    List<OrderStatusChange> findByOrderIdOrderByIdAsc(Long orderId);
}
