package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiKnowledgeEntry;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AiKnowledgeEntryRepository extends JpaRepository<AiKnowledgeEntry, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AiKnowledgeEntry e where e.id = :id")
    Optional<AiKnowledgeEntry> lock(@Param("id") Long id);
}
