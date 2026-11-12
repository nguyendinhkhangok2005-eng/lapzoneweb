package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "order_return_events", indexes = @Index(name = "idx_return_event_history", columnList = "request_id,id"))
@Getter @Setter
public class OrderReturnEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "request_id", nullable = false) private OrderReturnCase request;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "actor_id", nullable = false) private User actor;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 16) private OrderReturnCase.Status previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 16) private OrderReturnCase.Status newStatus;
    @Column(length = 2000) private String note;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
