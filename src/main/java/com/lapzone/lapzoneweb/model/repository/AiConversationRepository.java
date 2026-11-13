package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {
    Page<AiConversation> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
        Instant start, Instant end, Pageable pageable);

    @Query("""
        select cast(timestampadd(hour, 7, c.createdAt) as LocalDate), count(c)
        from AiConversation c where c.createdAt >= :start and c.createdAt < :end
        group by cast(timestampadd(hour, 7, c.createdAt) as LocalDate)
        order by cast(timestampadd(hour, 7, c.createdAt) as LocalDate)
        """)
    List<Object[]> countCreatedByVietnamDay(@Param("start") Instant start, @Param("end") Instant end);

    String ACCESS = "((:ownerId is not null and c.owner.id = :ownerId) or "
        + "(:ownerId is null and c.owner is null and c.guestKey = :guestKey))";

    @Query("select c from AiConversation c where " + ACCESS + " order by c.updatedAt desc, c.id desc")
    List<AiConversation> findAccessible(@Param("ownerId") Long ownerId, @Param("guestKey") String guestKey,
                                       Pageable pageable);

    @Query("select c from AiConversation c where c.id = :id and " + ACCESS)
    Optional<AiConversation> findAccessibleById(@Param("id") String id, @Param("ownerId") Long ownerId,
                                               @Param("guestKey") String guestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AiConversation c where c.id = :id and " + ACCESS)
    Optional<AiConversation> findAccessibleByIdForUpdate(@Param("id") String id, @Param("ownerId") Long ownerId,
                                                        @Param("guestKey") String guestKey);
}
