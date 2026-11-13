package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_status_history", indexes = @Index(name = "idx_appointment_history", columnList = "appointment_id,id"))
@Getter @Setter
public class AppointmentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false) private Appointment appointment;
    @Column(name = "previous_status", length = 30) private String previousStatus;
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 2000) private String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "changed_by") private User changedBy;
    @Column(name = "actor_role", nullable = false, length = 16) private String actorRole;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
}
