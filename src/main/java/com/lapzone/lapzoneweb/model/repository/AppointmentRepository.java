package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    long countByStatus(String status);
    interface Schedule {
        String getShowroom();
        LocalDate getAppointmentDate();
        java.time.LocalTime getAppointmentTime();
        Integer getDurationMinutes();
    }
    // Projection avoids caching a stale entity before waiting for the booking lock.
    @Query("select a.showroom as showroom, a.appointmentDate as appointmentDate, a.appointmentTime as appointmentTime, m.durationMinutes as durationMinutes from Appointment a left join a.management m where a.id = :id")
    Optional<Schedule> schedule(@Param("id") Long id);
    @Query("select a from Appointment a left join a.management m where (:admin = true or m.customer.id = :customerId) and (:status is null or a.status = :status)")
    Page<Appointment> accessiblePage(@Param("customerId") Long customerId, @Param("admin") boolean admin,
                                    @Param("status") String status, Pageable pageable);

    @Query("select a from Appointment a left join a.management m where a.id = :id and (:admin = true or m.customer.id = :customerId)")
    Optional<Appointment> accessible(@Param("id") Long id, @Param("customerId") Long customerId, @Param("admin") boolean admin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> lockById(@Param("id") Long id);

    // Locking read sees committed rows after the showroom/date mutex is acquired,
    // including with MySQL's default REPEATABLE READ transaction isolation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.appointmentDate = :date and lower(trim(a.showroom)) in :aliases and a.status in ('PENDING', 'CONFIRMED') order by a.id")
    List<Appointment> lockActiveBookings(@Param("date") LocalDate date, @Param("aliases") List<String> aliases);
}
