package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
    List<AiMessage> findByConversationIdOrderByIdAsc(String conversationId);
    List<AiMessage> findTop12ByConversationIdOrderByIdDesc(String conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AiMessage m where m.conversation.id = :conversationId")
    void deleteConversationMessages(@Param("conversationId") String conversationId);
}
