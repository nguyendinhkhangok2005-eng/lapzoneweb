package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiFeedback;
import com.lapzone.lapzoneweb.model.entity.AiFeedback.Rating;
import com.lapzone.lapzoneweb.model.entity.AiFeedback.Reason;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from AiFeedback f where f.id = :id")
    Optional<AiFeedback> lockForReview(@Param("id") Long id);
    Optional<AiFeedback> findByMessageId(Long messageId);
    List<AiFeedback> findByConversationId(String conversationId);
    long countByConversationIdAndCreatedAtGreaterThanEqual(String id, Instant since);

    @Query("""
        select f from AiFeedback f where f.createdAt >= :start and f.createdAt < :end
        and (:rating is null or f.rating = :rating) and (:reason is null or f.reason = :reason)
        order by f.createdAt desc, f.id desc
        """)
    Page<AiFeedback> reports(@Param("rating") Rating rating, @Param("reason") Reason reason,
        @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("select f.rating, count(f) from AiFeedback f where f.createdAt >= :start and f.createdAt < :end group by f.rating")
    List<Object[]> countRatings(@Param("start") Instant start, @Param("end") Instant end);

    List<AiFeedback> findByRatingAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
        Rating rating, Instant start, Instant end, Pageable pageable);
}
