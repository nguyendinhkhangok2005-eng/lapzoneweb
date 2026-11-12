package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages", indexes = {
        @Index(name = "idx_support_message_history", columnList = "conversation_id,id"),
        @Index(name = "idx_support_message_unread", columnList = "conversation_id,admin_message,read_at")})
@Getter @Setter
public class SupportMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private SupportConversation conversation;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    @Column(name = "admin_message", nullable = false)
    private boolean adminMessage;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
