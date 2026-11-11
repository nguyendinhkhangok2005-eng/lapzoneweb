package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.VoucherRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {
    // Locking reads are current reads on MySQL, including under REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VoucherRedemption r where r.voucher.id = :voucher and r.user.id = :user and r.state <> com.lapzone.lapzoneweb.model.entity.VoucherRedemption.State.RELEASED")
    List<VoucherRedemption> lockActiveUsage(@Param("voucher") Long voucherId, @Param("user") Long userId);
    long countByVoucherIdAndUserIdAndStateNot(Long voucherId, Long userId, VoucherRedemption.State state);
    boolean existsByCheckoutKey(String checkoutKey);
    Optional<VoucherRedemption> findByOrderId(Long orderId);
    Page<VoucherRedemption> findByVoucherIdOrderByIdDesc(Long voucherId, Pageable pageable);
}
