package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "appointment_booking_locks")
public class AppointmentBookingLock {
    @Id @Column(name = "lock_key", length = 64) private String key;
}
