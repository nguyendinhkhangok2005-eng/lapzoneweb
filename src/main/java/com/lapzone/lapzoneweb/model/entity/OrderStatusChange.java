package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "order_status_changes", indexes = @Index(name = "idx_order_status_history", columnList = "order_id,id"))
@Getter @Setter
public class OrderStatusChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) private Order order;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "actor_id", nullable = false) private User actor;
    @Column(name = "previous_status") private String previousStatus;
    @Column(name = "new_status", nullable = false) private String newStatus;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
}
