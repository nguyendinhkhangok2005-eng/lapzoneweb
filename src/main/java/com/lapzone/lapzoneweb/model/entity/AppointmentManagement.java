package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Optional extension keeps legacy appointment rows and their status column intact. */
@Entity
@Table(name = "appointment_management", indexes = @Index(name = "idx_appointment_customer_updated", columnList = "customer_id,updated_at"))
@Getter @Setter
public class AppointmentManagement {
    @Id @Column(name = "appointment_id") private Long id;
    @MapsId @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id") private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private User customer;
    @Column(name = "duration_minutes") private Integer durationMinutes;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "status_changed_at", nullable = false) private LocalDateTime statusChangedAt;
    @Column(name = "status_reason", length = 2000) private String statusReason;
}
