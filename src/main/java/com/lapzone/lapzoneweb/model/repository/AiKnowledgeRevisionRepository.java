package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiKnowledgeRevision;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AiKnowledgeRevisionRepository extends JpaRepository<AiKnowledgeRevision, Long> {
    Optional<AiKnowledgeRevision> findByEntryIdAndVersionNo(Long id, int version);
    Page<AiKnowledgeRevision> findByEntryIdOrderByVersionNoDesc(Long id, Pageable pageable);

    @EntityGraph(attributePaths = "entry")
    @Query("select r from AiKnowledgeRevision r where r.versionNo = r.entry.currentVersion order by r.entry.updatedAt desc, r.entry.id desc")
    Page<AiKnowledgeRevision> latest(Pageable pageable);

    @Query("""
        select r from AiKnowledgeRevision r
        where r.entry.state = :published
        and r.versionNo = r.entry.publishedVersion
        and ((:a <> '' and locate(:a, r.searchText) > 0) or (:b <> '' and locate(:b, r.searchText) > 0)
          or (:c <> '' and locate(:c, r.searchText) > 0) or (:d <> '' and locate(:d, r.searchText) > 0)
          or (:e <> '' and locate(:e, r.searchText) > 0) or (:f <> '' and locate(:f, r.searchText) > 0))
        order by r.entry.updatedAt desc, r.entry.id desc
        """)
    List<AiKnowledgeRevision> publishedCandidates(@Param("published") com.lapzone.lapzoneweb.model.entity.AiKnowledgeEntry.State published,
        @Param("a") String a, @Param("b") String b, @Param("c") String c,
        @Param("d") String d, @Param("e") String e, @Param("f") String f, Pageable pageable);
}
