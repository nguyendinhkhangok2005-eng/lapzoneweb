package com.lapzone.lapzoneweb.model.repository;
import com.lapzone.lapzoneweb.model.entity.OrderReturnCase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface OrderReturnCaseRepository extends JpaRepository<OrderReturnCase, Long> {
    @EntityGraph(attributePaths = {"order", "orderDetail"})
    List<OrderReturnCase> findByOrderIdIn(Collection<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderReturnCase c where c.order.id = :orderId")
    List<OrderReturnCase> lockByOrderId(@Param("orderId") Long orderId);

    @Query("select c.order.id from OrderReturnCase c where c.id = :id")
    Optional<Long> orderIdForRequest(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderReturnCase c where c.id = :id")
    Optional<OrderReturnCase> lockById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"order", "orderDetail", "orderDetail.product"})
    Page<OrderReturnCase> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"order", "orderDetail", "orderDetail.product"})
    Page<OrderReturnCase> findByStatus(OrderReturnCase.Status status, Pageable pageable);
}
