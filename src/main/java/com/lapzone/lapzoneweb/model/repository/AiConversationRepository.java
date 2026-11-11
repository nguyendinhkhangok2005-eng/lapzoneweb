package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {
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
