package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_conversations", indexes = {
        @Index(name = "idx_support_owner_updated", columnList = "customer_id,updated_at")})
@Getter @Setter
public class SupportConversation {
    public enum Status {
        WAITING("Chờ phản hồi"), REPLIED("Đã phản hồi"), CLOSED("Đã đóng");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(nullable = false, length = 120)
    private String subject;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Status status = Status.WAITING;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
