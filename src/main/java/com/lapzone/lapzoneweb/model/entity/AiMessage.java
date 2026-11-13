package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_messages", indexes = {
    @Index(name = "idx_ai_message_conversation", columnList = "conversation_id,id"),
    @Index(name = "idx_ai_message_created", columnList = "created_at,role")
})
public class AiMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Column(nullable = false, length = 8)
    private String role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String text;

    @Column(name = "product_ids", length = 100)
    private String productIds;

    @Column(name = "support_offered", nullable = false, columnDefinition = "boolean default false")
    private boolean supportOffered;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiMessage() {}

    public AiMessage(AiConversation conversation, String role, String text) {
        this.conversation = conversation;
        this.role = role;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public String getRole() { return role; }
    public Long getId() { return id; }
    public boolean isSupportOffered() { return supportOffered; }
    public void setSupportOffered(boolean supportOffered) { this.supportOffered = supportOffered; }
    public String getProductIds() { return productIds; }
    public void setProductIds(String productIds) { this.productIds = productIds; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
