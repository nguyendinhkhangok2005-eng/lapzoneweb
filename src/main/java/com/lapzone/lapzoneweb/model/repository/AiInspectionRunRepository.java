package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiInspectionRun;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AiInspectionRunRepository extends JpaRepository<AiInspectionRun, Long> {
    Optional<AiInspectionRun> findByRequestToken(String token);
    Optional<AiInspectionRun> findTopByAdminIdOrderByStartedAtDescIdDesc(Long id);
    long countByAdminIdAndStartedAtGreaterThanEqual(Long id, Instant start);
    Page<AiInspectionRun> findAllByOrderByStartedAtDescIdDesc(Pageable page);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AiInspectionRun r where r.id = :id")
    Optional<AiInspectionRun> lock(@Param("id") Long id);
}
