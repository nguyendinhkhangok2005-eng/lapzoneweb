package com.lapzone.lapzoneweb.model.repository;
import com.lapzone.lapzoneweb.model.entity.OrderItemReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface OrderItemReviewRepository extends JpaRepository<OrderItemReview, Long> {
    boolean existsByOrderDetailId(Long orderDetailId);
    @EntityGraph(attributePaths = {"orderDetail", "orderDetail.order"})
    List<OrderItemReview> findByOrderDetailOrderIdIn(Collection<Long> orderIds);
}
