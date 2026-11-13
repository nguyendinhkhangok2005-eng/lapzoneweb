package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.Instant;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
    java.util.Optional<AiMessage> findByIdAndConversationId(Long id, String conversationId);
    java.util.Optional<AiMessage> findFirstByConversationIdAndIdLessThanOrderByIdDesc(String conversationId, Long id);
    List<AiMessage> findByConversationIdAndIdLessThanOrderByIdDesc(String conversationId, Long id,
        org.springframework.data.domain.Pageable pageable);
    interface RecommendationCoverage {
        long getTracked();
        Long getRecommended();
    }

    @Query("""
        select m.role, count(m) from AiMessage m
        where m.createdAt >= :start and m.createdAt < :end
        and m.role in ('user', 'model') group by m.role
        """)
    List<Object[]> countByRoleInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        select count(m.productIds) as tracked,
            sum(case when m.productIds is not null and m.productIds <> '' then 1 else 0 end) as recommended
        from AiMessage m where m.role = 'model' and m.createdAt >= :start and m.createdAt < :end
        """)
    RecommendationCoverage recommendationCoverageInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    List<AiMessage> findByConversationIdOrderByIdAsc(String conversationId);
    List<AiMessage> findTop12ByConversationIdOrderByIdDesc(String conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AiMessage m where m.conversation.id = :conversationId")
    void deleteConversationMessages(@Param("conversationId") String conversationId);
}
