package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations", indexes = {
    @Index(name = "idx_ai_conversation_owner", columnList = "owner_id,updated_at"),
    @Index(name = "idx_ai_conversation_guest", columnList = "guest_key,updated_at")
})
public class AiConversation {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "guest_key", length = 36)
    private String guestKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiConversation() {}

    public AiConversation(User owner, String guestKey, String title) {
        this.id = UUID.randomUUID().toString();
        this.owner = owner;
        this.guestKey = guestKey;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = Instant.now(); }
    public void rename(String title) { this.title = title; touch(); }
}
