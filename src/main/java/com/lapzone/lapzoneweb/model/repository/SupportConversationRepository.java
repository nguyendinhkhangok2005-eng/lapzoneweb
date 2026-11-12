package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.SupportConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    @EntityGraph(attributePaths = "customer")
    @Query("select c from SupportConversation c where :admin = true or c.customer.id = :userId")
    Page<SupportConversation> inbox(@Param("userId") Long userId, @Param("admin") boolean admin, Pageable pageable);

    @Query("select c from SupportConversation c join fetch c.customer where c.id = :id and (:admin = true or c.customer.id = :userId)")
    Optional<SupportConversation> accessible(@Param("id") Long id, @Param("userId") Long userId, @Param("admin") boolean admin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupportConversation c where c.id = :id and (:admin = true or c.customer.id = :userId)")
    Optional<SupportConversation> lockAccessible(@Param("id") Long id, @Param("userId") Long userId, @Param("admin") boolean admin);
}
