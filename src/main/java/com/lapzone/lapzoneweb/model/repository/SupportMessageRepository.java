package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.SupportMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    Page<SupportMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);
    long countByConversationIdAndAdminMessageAndReadAtIsNull(Long conversationId, boolean adminMessage);

    interface UnreadCount { Long getConversationId(); long getUnreadCount(); }
    @Query("select m.conversation.id as conversationId, count(m) as unreadCount from SupportMessage m where m.conversation.id in :ids and m.adminMessage = :incomingAdmin and m.readAt is null group by m.conversation.id")
    List<UnreadCount> unreadByConversation(@Param("ids") Collection<Long> ids, @Param("incomingAdmin") boolean incomingAdmin);

    @Query("select count(m) from SupportMessage m where m.adminMessage = :incomingAdmin and m.readAt is null and (:admin = true or m.conversation.customer.id = :userId)")
    long unreadTotal(@Param("userId") Long userId, @Param("admin") boolean admin, @Param("incomingAdmin") boolean incomingAdmin);

    @Modifying
    @Query("update SupportMessage m set m.readAt = :now where m.conversation.id = :conversationId and m.id <= :throughId and m.adminMessage = :incomingAdmin and m.readAt is null")
    int markRead(@Param("conversationId") Long conversationId, @Param("throughId") Long throughId,
                 @Param("incomingAdmin") boolean incomingAdmin, @Param("now") LocalDateTime now);
}
