package com.lapzone.lapzoneweb.model.repository;
import com.lapzone.lapzoneweb.model.entity.OrderReturnEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderReturnEventRepository extends JpaRepository<OrderReturnEvent, Long> {
    List<OrderReturnEvent> findByRequestIdOrderByIdAsc(Long requestId);
}
