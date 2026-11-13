package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "ai_feedback", uniqueConstraints = @UniqueConstraint(name = "uk_ai_feedback_message", columnNames = "message_id"),
    indexes = {@Index(name = "idx_ai_feedback_created", columnList = "created_at,rating"),
        @Index(name = "idx_ai_feedback_filter", columnList = "rating,reason,created_at"),
        @Index(name = "idx_ai_feedback_conversation", columnList = "conversation_id,created_at")})
public class AiFeedback {
    public enum Rating {
        POSITIVE("Tích cực"), NEGATIVE("Tiêu cực");
        private final String label;
        Rating(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public enum Reason {
        INCORRECT("Thông tin sai."), IRRELEVANT("Không đúng nhu cầu."),
        BAD_RECOMMENDATION("Gợi ý sản phẩm không phù hợp."), UNCLEAR("Câu trả lời khó hiểu."), OTHER("Khác.");
        private final String label;
        Reason(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiMessage message;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiConversation conversation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Rating rating;
    @Enumerated(EnumType.STRING) @Column(length = 24)
    private Reason reason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    protected AiFeedback() {}
    public AiFeedback(AiMessage message, AiConversation conversation, Rating rating, Reason reason) {
        this.message = message; this.conversation = conversation; this.rating = rating;
        this.reason = reason; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public AiMessage getMessage() { return message; }
    public AiConversation getConversation() { return conversation; }
    public Rating getRating() { return rating; }
    public Reason getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void review(User admin) { if (reviewedAt == null) { reviewedAt = Instant.now(); reviewedBy = admin; } }
}
