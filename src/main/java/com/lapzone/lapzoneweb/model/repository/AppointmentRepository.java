package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
}
