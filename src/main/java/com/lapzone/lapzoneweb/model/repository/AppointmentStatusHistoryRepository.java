package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AppointmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    List<AppointmentStatusHistory> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
